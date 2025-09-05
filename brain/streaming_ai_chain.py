"""
Enhanced AI Chain with Native LLM Streaming Support

This module provides streaming AI response generation with token-by-token streaming
from language models, significantly improving perceived performance and user experience.

Features:
- Native streaming from OpenAI GPT models with SSE
- Streaming support for Anthropic Claude models
- Token-by-token progressive rendering
- Intelligent chunk aggregation and quality filtering
- Real-time progress tracking and ETA calculation
- Fallback handling for non-streaming scenarios
- Cost optimization through early stopping
"""

import asyncio
import logging
import os
import time
import gzip
import json
from typing import AsyncGenerator, Dict, Optional, Tuple, Any, List, Callable
from dataclasses import dataclass, field
from enum import Enum
from collections import deque
import statistics
from concurrent.futures import ThreadPoolExecutor

from langchain_core.language_models import BaseLanguageModel
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.callbacks.base import BaseCallbackHandler
from langchain_core.callbacks.manager import CallbackManager
from langchain_openai import ChatOpenAI
from langchain_core.messages import BaseMessage

from semantic_cache import get_semantic_cache, CacheEntry
from ai_optimizer import get_ai_optimizer, OptimizationProfile, RequestMetrics
from streaming_handler import get_streaming_handler, StreamingMode, StreamingConfig

try:
    from langchain_anthropic import ChatAnthropic
    ANTHROPIC_AVAILABLE = True
except ImportError:
    ANTHROPIC_AVAILABLE = False


logger = logging.getLogger(__name__)


@dataclass
class CircuitBreakerState:
    """Circuit breaker state for streaming reliability."""
    failure_count: int = 0
    last_failure_time: float = 0.0
    state: str = "CLOSED"  # CLOSED, OPEN, HALF_OPEN
    failure_threshold: int = 5
    recovery_timeout: float = 30.0


class CircuitBreaker:
    """Circuit breaker pattern for streaming reliability."""
    
    def __init__(self):
        self.state = CircuitBreakerState()
        
    async def call(self, func: Callable, *args, **kwargs):
        """Execute function with circuit breaker protection."""
        current_time = time.time()
        
        if self.state.state == "OPEN":
            if current_time - self.state.last_failure_time > self.state.recovery_timeout:
                self.state.state = "HALF_OPEN"
                logger.info("Circuit breaker moved to HALF_OPEN state")
            else:
                raise Exception("Circuit breaker OPEN - service unavailable")
        
        try:
            result = await func(*args, **kwargs) if asyncio.iscoroutinefunction(func) else func(*args, **kwargs)
            
            if self.state.state == "HALF_OPEN":
                self.state.state = "CLOSED"
                self.state.failure_count = 0
                logger.info("Circuit breaker reset to CLOSED state")
                
            return result
            
        except Exception as e:
            self.state.failure_count += 1
            self.state.last_failure_time = current_time
            
            if self.state.failure_count >= self.state.failure_threshold:
                self.state.state = "OPEN"
                logger.warning(f"Circuit breaker OPENED after {self.state.failure_count} failures")
            
            raise e


class ContextAnalyzer:
    """Analyzes content context for intelligent chunking."""
    
    def __init__(self):
        self.sentence_boundaries = ['. ', '! ', '? ', '.\\n', '!\\n', '?\\n']
        self.paragraph_boundaries = ['\\n\\n', '\\n- ', '\\n1. ', '\\n2. ']
        
    def analyze_context_awareness(self, content: str) -> float:
        """Calculate context awareness score for content."""
        if not content.strip():
            return 0.0
            
        score = 0.0
        
        # Sentence completeness
        complete_sentences = sum(1 for boundary in self.sentence_boundaries if boundary in content)
        total_content_len = len(content)
        
        if total_content_len > 0:
            sentence_ratio = complete_sentences / max(1, total_content_len / 50)  # Normalize by length
            score += min(0.4, sentence_ratio * 0.1)
        
        # Paragraph structure
        paragraphs = sum(1 for boundary in self.paragraph_boundaries if boundary in content)
        if paragraphs > 0:
            score += min(0.3, paragraphs * 0.15)
            
        # Coherence indicators (common transition words)
        coherence_words = ['however', 'therefore', 'additionally', 'furthermore', 'moreover', 'consequently']
        coherence_count = sum(1 for word in coherence_words if word.lower() in content.lower())
        score += min(0.3, coherence_count * 0.1)
        
        return min(1.0, score)
    
    def find_optimal_break_points(self, content: str) -> List[int]:
        """Find optimal break points for chunking."""
        break_points = []
        
        # Find sentence boundaries
        for boundary in self.sentence_boundaries:
            idx = 0
            while idx < len(content):
                pos = content.find(boundary, idx)
                if pos == -1:
                    break
                break_points.append(pos + len(boundary))
                idx = pos + 1
        
        # Find paragraph boundaries (higher priority)
        for boundary in self.paragraph_boundaries:
            idx = 0
            while idx < len(content):
                pos = content.find(boundary, idx)
                if pos == -1:
                    break
                break_points.append(pos + len(boundary))
                idx = pos + 1
                
        return sorted(list(set(break_points)))


class CompressionHandler:
    """Handles streaming compression and efficient serialization."""
    
    def __init__(self):
        self.compression_threshold = 500  # Compress if content > 500 chars
        
    def compress_content(self, content: str) -> Tuple[bytes, float]:
        """Compress content and return compression ratio."""
        if len(content) < self.compression_threshold:
            return content.encode('utf-8'), 1.0
            
        try:
            original_size = len(content.encode('utf-8'))
            compressed = gzip.compress(content.encode('utf-8'))
            compression_ratio = len(compressed) / original_size
            
            return compressed, compression_ratio
            
        except Exception as e:
            logger.warning(f"Compression failed: {e}")
            return content.encode('utf-8'), 1.0
    
    def decompress_content(self, compressed_data: bytes, is_compressed: bool = True) -> str:
        """Decompress content."""
        if not is_compressed:
            return compressed_data.decode('utf-8')
            
        try:
            return gzip.decompress(compressed_data).decode('utf-8')
        except Exception as e:
            logger.warning(f"Decompression failed: {e}")
            return compressed_data.decode('utf-8')
    
    def serialize_efficiently(self, data: Dict[str, Any]) -> str:
        """Efficient JSON serialization with minimal whitespace."""
        return json.dumps(data, separators=(',', ':'), ensure_ascii=False)


class ChunkOptimizer:
    """Optimizes chunk sizes based on content and performance metrics."""
    
    def __init__(self):
        self.performance_history = deque(maxlen=100)
        self.optimal_chunk_sizes = deque(maxlen=20)
        self.base_chunk_size = 50
        
    def calculate_optimal_chunk_size(
        self, 
        content: str, 
        current_velocity: float,
        context_score: float,
        target_latency_ms: int = 100
    ) -> int:
        """Calculate optimal chunk size based on current conditions."""
        
        # Base calculation
        optimal_size = self.base_chunk_size
        
        # Adjust based on token velocity
        if current_velocity > 0:
            # Higher velocity allows larger chunks
            velocity_multiplier = min(2.0, current_velocity / 10.0)  # tokens/second
            optimal_size = int(optimal_size * velocity_multiplier)
        
        # Adjust based on context awareness
        if context_score > 0.7:
            # High context awareness allows larger chunks
            optimal_size = int(optimal_size * 1.5)
        elif context_score < 0.3:
            # Low context awareness requires smaller chunks
            optimal_size = int(optimal_size * 0.7)
        
        # Consider target latency
        latency_adjustment = max(0.5, target_latency_ms / 100.0)
        optimal_size = int(optimal_size * latency_adjustment)
        
        # Store for learning
        self.optimal_chunk_sizes.append(optimal_size)
        
        # Learn from history
        if len(self.optimal_chunk_sizes) >= 10:
            avg_optimal = statistics.mean(self.optimal_chunk_sizes)
            optimal_size = int(0.7 * optimal_size + 0.3 * avg_optimal)
        
        return max(10, min(200, optimal_size))  # Bounds checking
    
    def record_performance(self, chunk_size: int, latency_ms: float, quality_score: float):
        """Record performance metrics for learning."""
        self.performance_history.append({
            'chunk_size': chunk_size,
            'latency_ms': latency_ms,
            'quality_score': quality_score,
            'timestamp': time.time()
        })


class PredictiveBuffer:
    """Predictive buffering for smoother streaming experience."""
    
    def __init__(self, max_buffer_size: int = 1000):
        self.buffer = deque(maxlen=max_buffer_size)
        self.prediction_model = TokenPredictor()
        self.prefetch_executor = ThreadPoolExecutor(max_workers=2)
        
    async def add_token(self, token: str, confidence: float = 1.0):
        """Add token with confidence score."""
        self.buffer.append({
            'token': token,
            'confidence': confidence,
            'timestamp': time.time()
        })
        
        # Trigger prediction for next tokens
        if len(self.buffer) >= 5:
            await self._predict_next_tokens()
    
    async def _predict_next_tokens(self):
        """Predict likely next tokens for prefetching."""
        try:
            recent_tokens = [item['token'] for item in list(self.buffer)[-10:]]
            predicted = await self.prediction_model.predict_next_tokens(recent_tokens)
            
            # Store predictions with lower confidence
            for pred_token in predicted:
                await self.add_token(pred_token, confidence=0.3)
                
        except Exception as e:
            logger.debug(f"Token prediction failed: {e}")
    
    def get_buffered_content(self, min_confidence: float = 0.5) -> str:
        """Get buffered content above confidence threshold."""
        return ''.join(
            item['token'] for item in self.buffer 
            if item['confidence'] >= min_confidence
        )


class TokenPredictor:
    """Simple token prediction for prefetching."""
    
    def __init__(self):
        self.common_patterns = {
            'Dear': ['Hiring', 'Manager', 'Team'],
            'I': ['am', 'have', 'would', 'believe'],
            'with': ['experience', 'expertise', 'a', 'the'],
            'and': ['I', 'my', 'have', 'am']
        }
    
    async def predict_next_tokens(self, recent_tokens: List[str], max_predictions: int = 3) -> List[str]:
        """Predict next likely tokens based on recent context."""
        if not recent_tokens:
            return []
            
        last_token = recent_tokens[-1].strip().lower()
        
        # Simple pattern matching
        if last_token in self.common_patterns:
            return self.common_patterns[last_token][:max_predictions]
        
        # Fallback predictions based on common cover letter patterns
        fallback_predictions = [' ', 'the', 'and', 'I', 'to']
        return fallback_predictions[:max_predictions]


class StreamingPhase(Enum):
    """Different phases of streaming AI generation."""
    INITIALIZING = "initializing"
    JOB_PARSING = "job_parsing"
    AI_GENERATION = "ai_generation"
    QUALITY_CHECK = "quality_check"
    FINALIZING = "finalizing"
    COMPLETED = "completed"


@dataclass
class StreamingProgress:
    """Enhanced detailed progress tracking for streaming responses."""
    job_id: str
    phase: StreamingPhase
    progress_percentage: float
    tokens_generated: int
    estimated_total_tokens: int
    words_generated: int
    estimated_completion_time: Optional[float]
    current_content: str
    quality_score: float
    cost_so_far: float
    started_at: float
    phase_started_at: float
    # Enhanced fields for advanced streaming
    token_velocity: float = 0.0  # tokens/second
    predictive_buffer_size: int = 0
    compression_ratio: float = 1.0
    context_awareness_score: float = 0.0
    circuit_breaker_state: str = "CLOSED"
    adaptive_chunk_sizes: List[int] = field(default_factory=list)
    streaming_health_score: float = 1.0
    

class EnhancedStreamingCallbackHandler(BaseCallbackHandler):
    """Enhanced callback handler with predictive buffering and compression."""
    
    def __init__(self, job_id: str, progress_tracker: StreamingProgress):
        super().__init__()
        self.job_id = job_id
        self.progress = progress_tracker
        self.token_buffer = ""
        self.tokens_received = 0
        # Enhanced features
        self.predictive_buffer = PredictiveBuffer()
        self.token_timestamps = deque(maxlen=100)  # Track token timing
        self.context_analyzer = ContextAnalyzer()
        self.compression_handler = CompressionHandler()
        self.circuit_breaker = CircuitBreaker()
        self.chunk_optimizer = ChunkOptimizer()
        
    def on_llm_new_token(self, token: str, **kwargs: Any) -> None:
        """Enhanced token handling with predictive buffering and analytics."""
        current_time = time.time()
        
        # Circuit breaker check
        if self.progress.circuit_breaker_state == "OPEN":
            logger.warning(f"Circuit breaker OPEN for job {self.job_id} - skipping token")
            return
        
        self.tokens_received += 1
        self.progress.tokens_generated += 1
        self.token_buffer += token
        
        # Track token timing for velocity calculation
        self.token_timestamps.append(current_time)
        
        # Add to predictive buffer
        asyncio.create_task(self.predictive_buffer.add_token(token))
        
        # Calculate token velocity
        if len(self.token_timestamps) >= 10:
            recent_timestamps = list(self.token_timestamps)[-10:]
            time_span = recent_timestamps[-1] - recent_timestamps[0]
            if time_span > 0:
                self.progress.token_velocity = 10.0 / time_span
        
        # Update context awareness
        self.progress.context_awareness_score = self.context_analyzer.analyze_context_awareness(
            self.token_buffer
        )
        
        # Calculate optimal chunk size
        optimal_chunk_size = self.chunk_optimizer.calculate_optimal_chunk_size(
            self.token_buffer,
            self.progress.token_velocity,
            self.progress.context_awareness_score
        )
        self.progress.adaptive_chunk_sizes.append(optimal_chunk_size)
        
        # Update progress based on estimated completion
        if self.progress.estimated_total_tokens > 0:
            self.progress.progress_percentage = min(
                0.95, self.progress.tokens_generated / self.progress.estimated_total_tokens
            )
        
        # Enhanced completion time estimation
        elapsed = current_time - self.progress.phase_started_at
        if elapsed > 1.0 and self.tokens_received > 10:
            # Use exponential moving average for smoother estimates
            current_rate = self.progress.token_velocity
            if current_rate > 0:
                remaining_tokens = max(0, self.progress.estimated_total_tokens - self.progress.tokens_generated)
                estimated_time = remaining_tokens / current_rate
                
                # Smooth the estimate
                if self.progress.estimated_completion_time is not None:
                    self.progress.estimated_completion_time = (
                        0.7 * self.progress.estimated_completion_time + 0.3 * estimated_time
                    )
                else:
                    self.progress.estimated_completion_time = estimated_time
        
        # Update streaming health score based on performance
        self._update_streaming_health_score()
        
        logger.debug(
            f"Enhanced token received for job {self.job_id}: '{token}' "
            f"(total: {self.tokens_received}, velocity: {self.progress.token_velocity:.2f} t/s, "
            f"context: {self.progress.context_awareness_score:.2f}, "
            f"health: {self.progress.streaming_health_score:.2f})"
        )
    
    def _update_streaming_health_score(self):
        """Update streaming health score based on multiple factors."""
        # Velocity health (optimal range: 5-20 tokens/second)
        velocity_health = 1.0
        if 5.0 <= self.progress.token_velocity <= 20.0:
            velocity_health = 1.0
        elif self.progress.token_velocity < 1.0:
            velocity_health = 0.3  # Very slow
        elif self.progress.token_velocity > 50.0:
            velocity_health = 0.7  # Too fast, might indicate issues
        else:
            velocity_health = 0.8
        
        # Context health
        context_health = min(1.0, self.progress.context_awareness_score + 0.3)
        
        # Circuit breaker health
        circuit_health = 1.0 if self.progress.circuit_breaker_state == "CLOSED" else 0.3
        
        # Combine factors
        self.progress.streaming_health_score = (
            0.4 * velocity_health +
            0.3 * context_health +
            0.3 * circuit_health
        )
    
    def get_buffer_and_clear(self) -> str:
        """Get current buffer content and clear it."""
        content = self.token_buffer
        self.token_buffer = ""
        return content
    
    async def get_enhanced_buffer_content(self, include_predictions: bool = False) -> Dict[str, Any]:
        """Get enhanced buffer content with compression and predictions."""
        content = self.token_buffer
        
        # Get compressed content
        compressed_data, compression_ratio = self.compression_handler.compress_content(content)
        self.progress.compression_ratio = compression_ratio
        
        # Prepare enhanced response
        enhanced_content = {
            "content": content,
            "compressed_size": len(compressed_data),
            "original_size": len(content.encode('utf-8')),
            "compression_ratio": compression_ratio,
            "context_score": self.progress.context_awareness_score,
            "optimal_break_points": self.context_analyzer.find_optimal_break_points(content)
        }
        
        # Include predictions if requested
        if include_predictions:
            predicted_content = self.predictive_buffer.get_buffered_content(min_confidence=0.3)
            enhanced_content["predicted_content"] = predicted_content
            enhanced_content["prediction_confidence"] = 0.3
        
        return enhanced_content


async def create_streaming_llm(
    model_provider: str = "openai", 
    model_name: Optional[str] = None, 
    temperature: float = 0.7,
    streaming: bool = True
) -> BaseLanguageModel:
    """Create a streaming-enabled LLM instance."""
    
    if model_provider.lower() == "openai":
        if not os.getenv("OPENAI_API_KEY"):
            raise ValueError("OpenAI API key not found. Set OPENAI_API_KEY environment variable.")
        
        model = model_name or "gpt-4o"
        return ChatOpenAI(
            model=model, 
            temperature=temperature,
            streaming=streaming,
            max_tokens=2000,  # Reasonable limit for cover letters
            timeout=120  # 2-minute timeout
        )
    
    elif model_provider.lower() == "anthropic":
        if not ANTHROPIC_AVAILABLE:
            raise ValueError("Anthropic integration not available. Install langchain-anthropic package.")
        
        if not os.getenv("ANTHROPIC_API_KEY"):
            raise ValueError("Anthropic API key not found. Set ANTHROPIC_API_KEY environment variable.")
        
        model = model_name or "claude-3-5-sonnet-20241022"
        return ChatAnthropic(
            model_name=model, 
            temperature=temperature, 
            timeout=120,
            stop=[],
            streaming=streaming,
            max_tokens=2000
        )
    
    else:
        raise ValueError(f"Unsupported model provider: {model_provider}")


async def create_streaming_cover_letter_chain(
    jd_text: str,
    model_provider: Optional[str] = None,
    model_name: Optional[str] = None,
    user_id: Optional[str] = None,
    job_id: Optional[str] = None,
    optimization_profile: str = "balanced",
    enable_streaming: bool = True,
    enable_caching: bool = True
) -> AsyncGenerator[Dict[str, Any], None]:
    """
    Create a streaming cover letter generation pipeline with real-time token streaming.
    
    This function provides token-by-token streaming of AI responses, giving users
    immediate feedback and significantly improving perceived performance.
    
    Args:
        jd_text: Job description text to process
        model_provider: AI provider ("openai" or "anthropic")  
        model_name: Specific model name to use
        user_id: User identifier for analytics
        job_id: Job identifier for tracking
        optimization_profile: Optimization strategy
        enable_streaming: Whether to enable streaming responses
        enable_caching: Whether to use semantic caching
        
    Yields:
        Dictionary containing streaming updates with progress and content
    """
    
    start_time = time.time()
    job_id = job_id or f"stream_{int(time.time())}"
    logger.info(f"Starting streaming cover letter generation for job {job_id}")
    
    # Initialize components
    semantic_cache = get_semantic_cache() if enable_caching else None
    ai_optimizer = get_ai_optimizer()
    streaming_handler = get_streaming_handler()
    
    # Initialize progress tracking
    progress = StreamingProgress(
        job_id=job_id,
        phase=StreamingPhase.INITIALIZING,
        progress_percentage=0.0,
        tokens_generated=0,
        estimated_total_tokens=350,  # Typical cover letter length
        words_generated=0,
        estimated_completion_time=None,
        current_content="",
        quality_score=0.0,
        cost_so_far=0.0,
        started_at=start_time,
        phase_started_at=start_time
    )
    
    try:
        # Phase 1: Job Description Parsing
        progress.phase = StreamingPhase.JOB_PARSING
        progress.phase_started_at = time.time()
        progress.progress_percentage = 0.05
        
        yield {
            "job_id": job_id,
            "phase": progress.phase.value,
            "progress": progress.progress_percentage,
            "message": "Analyzing job description...",
            "streaming": True,
            "timestamp": time.time()
        }
        
        # Parse job description (simplified for streaming)
        parsed_jd = await _parse_job_description_fast(jd_text)
        logger.info(f"Parsed JD: company={parsed_jd.get('company')}, role={parsed_jd.get('role')}")
        
        # Phase 2: Cache Check (if enabled)
        if semantic_cache:
            progress.progress_percentage = 0.15
            yield {
                "job_id": job_id,
                "phase": "cache_checking",
                "progress": progress.progress_percentage,
                "message": "Checking for similar applications...",
                "streaming": True,
                "timestamp": time.time()
            }
            
            cached_response = await semantic_cache.get_cached_response(
                jd_text, model_provider or "openai", model_name or "auto", parsed_jd
            )
            
            if cached_response:
                logger.info(f"Cache hit! Using cached response with {cached_response.quality_score:.2f} quality")
                yield {
                    "job_id": job_id,
                    "phase": "completed",
                    "progress": 1.0,
                    "content": cached_response.content,
                    "cached": True,
                    "quality_score": cached_response.quality_score,
                    "cost_saved": cached_response.cost_usd,
                    "streaming": False,
                    "complete": True,
                    "timestamp": time.time()
                }
                return
        
        # Phase 3: AI Generation with Streaming
        progress.phase = StreamingPhase.AI_GENERATION  
        progress.phase_started_at = time.time()
        progress.progress_percentage = 0.20
        
        yield {
            "job_id": job_id,
            "phase": progress.phase.value,
            "progress": progress.progress_percentage,
            "message": "Generating personalized cover letter...",
            "streaming": True,
            "timestamp": time.time()
        }
        
        # Create streaming LLM
        model_provider = model_provider or "openai"
        llm = await create_streaming_llm(
            model_provider=model_provider,
            model_name=model_name,
            streaming=enable_streaming
        )
        
        # Prepare streaming callback handler
        callback_handler = EnhancedStreamingCallbackHandler(job_id, progress)
        callback_manager = CallbackManager([callback_handler])
        
        # Create optimized prompt for cover letter generation
        prompt = ChatPromptTemplate.from_messages([
            ("system", """You are an expert career advisor and professional writer specializing in creating compelling cover letters. 

Your task is to write a personalized, professional cover letter that:
1. Addresses specific requirements mentioned in the job description
2. Highlights relevant skills and experiences
3. Shows genuine enthusiasm for the company and role
4. Maintains a professional yet engaging tone
5. Is concise but comprehensive (200-400 words)

Write in a natural, conversational style that feels authentic and personal."""),
            
            ("human", """Please write a compelling cover letter for this job opportunity:

Job Description: {job_description}

Company: {company}
Role: {role}
Key Skills Required: {skills}

Create a personalized cover letter that addresses the specific requirements and shows enthusiasm for this opportunity.""")
        ])
        
        # Fill in template variables
        formatted_prompt = prompt.format_messages(
            job_description=jd_text[:2000],  # Limit length for efficiency
            company=parsed_jd.get("company", "the company"),
            role=parsed_jd.get("role", "this position"),
            skills=", ".join(parsed_jd.get("skills", ["relevant experience"])[:5])
        )
        
        # Stream the AI response
        streaming_content = ""
        chunk_count = 0
        last_yield_time = time.time()
        
        if enable_streaming:
            # Use streaming mode
            async for chunk in llm.astream(formatted_prompt, callbacks=callback_manager):
                chunk_count += 1
                chunk_text = chunk.content if hasattr(chunk, 'content') else str(chunk)
                streaming_content += chunk_text
                progress.current_content = streaming_content
                
                # Update word count
                progress.words_generated = len(streaming_content.split())
                
                # Calculate dynamic quality score
                progress.quality_score = await _calculate_streaming_quality(
                    streaming_content, parsed_jd, progress.words_generated
                )
                
                # Update progress based on content length
                target_words = 250  # Target cover letter length
                word_progress = min(0.9, progress.words_generated / target_words)
                progress.progress_percentage = 0.20 + (word_progress * 0.7)
                
                # Intelligent chunking - yield when we have meaningful content
                should_yield = (
                    chunk_count % 5 == 0 or  # Every 5 tokens
                    len(chunk_text.strip()) > 0 and chunk_text.strip().endswith(('.', '!', '?')) or  # Sentence endings
                    time.time() - last_yield_time > 0.5  # At least every 500ms
                )
                
                if should_yield and len(streaming_content.strip()) > 20:
                    # Clean up partial content for display
                    display_content = streaming_content.strip()
                    if not display_content.endswith(('.', '!', '?', ',', ';', ':')):
                        display_content += "..."
                    
                    yield {
                        "job_id": job_id,
                        "phase": progress.phase.value,
                        "progress": progress.progress_percentage,
                        "content": display_content,
                        "partial": True,
                        "streaming": True,
                        "tokens_generated": progress.tokens_generated,
                        "words_generated": progress.words_generated,
                        "quality_score": progress.quality_score,
                        "estimated_completion": progress.estimated_completion_time,
                        "timestamp": time.time()
                    }
                    last_yield_time = time.time()
        else:
            # Non-streaming fallback
            response = await llm.ainvoke(formatted_prompt, callbacks=callback_manager)
            streaming_content = response.content if hasattr(response, 'content') else str(response)
            progress.current_content = streaming_content
            progress.words_generated = len(streaming_content.split())
        
        # Phase 4: Quality Check and Finalization
        progress.phase = StreamingPhase.QUALITY_CHECK
        progress.phase_started_at = time.time() 
        progress.progress_percentage = 0.95
        
        yield {
            "job_id": job_id,
            "phase": progress.phase.value,
            "progress": progress.progress_percentage,
            "message": "Finalizing and quality checking...",
            "streaming": True,
            "timestamp": time.time()
        }
        
        # Final quality assessment
        final_quality = await _calculate_final_quality(streaming_content, parsed_jd)
        
        # Calculate estimated cost
        estimated_cost = _estimate_generation_cost(
            model_provider, model_name, progress.tokens_generated
        )
        
        # Cache the result if quality is good
        if enable_caching and semantic_cache and final_quality >= 0.7:
            await semantic_cache.cache_response(
                jd_text=jd_text,
                response_content=streaming_content,
                parsed_jd=parsed_jd,
                model_provider=model_provider,
                model_name=model_name or "auto",
                token_count=progress.tokens_generated,
                cost_usd=estimated_cost
            )
        
        # Phase 5: Completed
        progress.phase = StreamingPhase.COMPLETED
        progress.progress_percentage = 1.0
        total_duration = time.time() - start_time
        
        # Final result
        yield {
            "job_id": job_id,
            "phase": progress.phase.value,
            "progress": progress.progress_percentage,
            "content": streaming_content,
            "partial": False,
            "streaming": False,
            "complete": True,
            "quality_score": final_quality,
            "words_generated": progress.words_generated,
            "tokens_generated": progress.tokens_generated,
            "estimated_cost": estimated_cost,
            "generation_time_ms": int(total_duration * 1000),
            "cached": False,
            "timestamp": time.time(),
            "metadata": {
                "model_provider": model_provider,
                "model_name": model_name,
                "optimization_profile": optimization_profile,
                "company": parsed_jd.get("company"),
                "role": parsed_jd.get("role")
            }
        }
        
    except Exception as e:
        logger.error(f"Streaming generation error for job {job_id}: {e}")
        
        # Return error with fallback content if available
        error_response = {
            "job_id": job_id,
            "phase": "error",
            "progress": 0.0,
            "error": str(e),
            "streaming": False,
            "timestamp": time.time()
        }
        
        # If we have partial content, include it
        if progress.current_content.strip():
            error_response.update({
                "content": progress.current_content,
                "partial_content_available": True,
                "quality_score": progress.quality_score
            })
        
        yield error_response


async def _parse_job_description_fast(jd_text: str) -> Dict[str, Any]:
    """Fast job description parsing for streaming scenarios."""
    
    # Quick heuristic-based parsing
    jd_lower = jd_text.lower()
    
    # Extract company name (simple heuristics)
    company = "the company"
    company_patterns = ["company:", "organization:", "employer:", "at "]
    for pattern in company_patterns:
        if pattern in jd_lower:
            idx = jd_lower.find(pattern)
            if idx >= 0:
                candidate = jd_text[idx + len(pattern):idx + len(pattern) + 50].strip()
                if candidate and len(candidate.split()[0]) > 2:
                    company = candidate.split()[0].replace(",", "").replace(".", "")
                    break
    
    # Extract role (look for common patterns)
    role = "this position"
    role_patterns = ["title:", "position:", "role:", "job:"]
    for pattern in role_patterns:
        if pattern in jd_lower:
            idx = jd_lower.find(pattern)
            if idx >= 0:
                candidate = jd_text[idx + len(pattern):idx + len(pattern) + 100].strip()
                if candidate:
                    role = candidate.split('\n')[0].strip()
                    break
    
    # Extract skills (simple keyword matching)
    skill_keywords = [
        "python", "java", "javascript", "react", "node", "aws", "docker", 
        "kubernetes", "sql", "postgresql", "mongodb", "redis", "git",
        "machine learning", "ai", "data science", "analytics", "api"
    ]
    
    skills = []
    for skill in skill_keywords:
        if skill in jd_lower:
            skills.append(skill.title())
    
    return {
        "company": company,
        "role": role,
        "skills": skills[:10],  # Limit to top 10
        "parsed_at": time.time()
    }


async def _calculate_streaming_quality(content: str, parsed_jd: Dict[str, Any], word_count: int) -> float:
    """Calculate quality score for streaming content."""
    score = 0.5  # Base score
    
    # Length scoring
    if 50 <= word_count <= 300:
        score += 0.2
    elif word_count > 300:
        score += 0.1
    
    # Company mention
    company = parsed_jd.get("company", "").lower()
    if company and company in content.lower():
        score += 0.2
    
    # Skills mention
    skills = parsed_jd.get("skills", [])
    skills_mentioned = sum(1 for skill in skills if skill.lower() in content.lower())
    if skills_mentioned >= 2:
        score += 0.3
    elif skills_mentioned >= 1:
        score += 0.2
    
    # Structure quality (simple heuristics)
    if content.count('.') >= 3:  # Multiple sentences
        score += 0.1
    
    return min(1.0, max(0.0, score))


async def _calculate_final_quality(content: str, parsed_jd: Dict[str, Any]) -> float:
    """Calculate final quality score for completed content."""
    score = 0.0
    
    # Word count scoring (200-400 is ideal)
    word_count = len(content.split())
    if 200 <= word_count <= 400:
        score += 0.3
    elif 150 <= word_count <= 500:
        score += 0.2
    elif word_count >= 100:
        score += 0.1
    
    # Company mention
    company = parsed_jd.get("company", "").lower()
    if company and company in content.lower():
        score += 0.2
    
    # Role mention  
    role = parsed_jd.get("role", "").lower()
    if role and any(word in content.lower() for word in role.split()):
        score += 0.2
    
    # Skills coverage
    skills = parsed_jd.get("skills", [])
    skills_mentioned = sum(1 for skill in skills if skill.lower() in content.lower())
    skill_coverage = min(1.0, skills_mentioned / max(1, len(skills)))
    score += skill_coverage * 0.3
    
    return min(1.0, max(0.0, score))


def _estimate_generation_cost(model_provider: str, model_name: Optional[str], tokens_used: int) -> float:
    """Estimate the cost of AI generation."""
    
    # Pricing as of 2024 (approximate)
    pricing = {
        "openai": {
            "gpt-4o": 0.03 / 1000,  # $0.03 per 1K tokens
            "gpt-4": 0.03 / 1000,
            "gpt-3.5-turbo": 0.002 / 1000  # $0.002 per 1K tokens
        },
        "anthropic": {
            "claude-3-5-sonnet-20241022": 0.024 / 1000,  # Approximate
            "claude-3-opus": 0.075 / 1000,
            "claude-3-sonnet": 0.012 / 1000
        }
    }
    
    provider_pricing = pricing.get(model_provider.lower(), {})
    model_key = model_name or ("gpt-4o" if model_provider.lower() == "openai" else "claude-3-5-sonnet-20241022")
    cost_per_token = provider_pricing.get(model_key, 0.03 / 1000)  # Default fallback
    
    return tokens_used * cost_per_token