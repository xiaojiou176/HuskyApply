"""
Advanced AI Model Router with Intelligent Selection and Load Balancing

This module provides enterprise-grade model routing capabilities with:
- Intelligent model selection based on job analysis and user requirements
- Dynamic load balancing across model providers
- Cost optimization and performance tracking
- Failover and circuit breaker patterns
- Real-time model performance analytics
"""

import asyncio
import logging
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Dict, List, Optional, Tuple, Any
from collections import defaultdict, deque
import json
import hashlib

from prometheus_client import Counter, Histogram, Gauge


logger = logging.getLogger(__name__)


class ModelProvider(Enum):
    """AI model providers."""
    OPENAI = "openai"
    ANTHROPIC = "anthropic"
    GOOGLE = "google"
    COHERE = "cohere"


class ModelCapability(Enum):
    """Model capabilities for matching to job requirements."""
    TECHNICAL_WRITING = "technical_writing"
    CREATIVE_WRITING = "creative_writing"
    ANALYTICAL_REASONING = "analytical_reasoning"
    CODE_UNDERSTANDING = "code_understanding"
    MULTILINGUAL = "multilingual"
    LONG_CONTEXT = "long_context"
    FAST_RESPONSE = "fast_response"
    COST_EFFECTIVE = "cost_effective"


@dataclass
class ModelConfig:
    """Enhanced model configuration with capabilities and performance metrics."""
    name: str
    provider: ModelProvider
    capabilities: List[ModelCapability]
    cost_per_token: float
    avg_response_time_ms: float
    context_window: int
    output_token_limit: int
    quality_score: float  # 0.0 to 1.0
    availability_score: float = 1.0  # Dynamic availability based on recent performance
    max_concurrent_requests: int = 10
    current_load: int = 0
    enabled: bool = True


@dataclass
class RoutingRequest:
    """Request for model routing with context and requirements."""
    job_description: str
    user_preferences: Dict[str, Any]
    complexity_level: str
    required_capabilities: List[ModelCapability]
    max_cost_per_request: Optional[float] = None
    max_response_time_ms: Optional[int] = None
    quality_threshold: float = 0.7
    user_id: str = ""
    trace_id: str = ""


@dataclass
class RoutingResult:
    """Result of model routing with rationale and metadata."""
    provider: ModelProvider
    model_name: str
    config: ModelConfig
    selection_reason: str
    confidence_score: float
    estimated_cost: float
    estimated_time_ms: float
    fallback_models: List[Tuple[ModelProvider, str]]


class LoadBalancer:
    """Intelligent load balancer for model requests."""
    
    def __init__(self):
        self.model_loads = defaultdict(int)
        self.response_times = defaultdict(lambda: deque(maxlen=100))
        self.success_rates = defaultdict(lambda: deque(maxlen=100))
        self.last_used = defaultdict(float)
    
    def select_least_loaded_model(self, candidates: List[ModelConfig]) -> Optional[ModelConfig]:
        """Select the model with lowest current load."""
        if not candidates:
            return None
        
        # Filter by availability and load capacity
        available = [m for m in candidates 
                    if m.enabled and m.current_load < m.max_concurrent_requests]
        
        if not available:
            logger.warning("No available models with capacity")
            return candidates[0]  # Return first as fallback
        
        # Sort by load factor (current_load / max_concurrent_requests)
        available.sort(key=lambda m: m.current_load / max(m.max_concurrent_requests, 1))
        
        selected = available[0]
        self.last_used[selected.name] = time.time()
        
        return selected
    
    def update_model_performance(self, model_name: str, response_time_ms: float, success: bool):
        """Update performance metrics for a model."""
        self.response_times[model_name].append(response_time_ms)
        self.success_rates[model_name].append(1.0 if success else 0.0)
    
    def get_model_health_score(self, model_name: str) -> float:
        """Calculate health score based on recent performance."""
        if model_name not in self.response_times:
            return 1.0  # New model gets benefit of doubt
        
        recent_times = list(self.response_times[model_name])
        recent_successes = list(self.success_rates[model_name])
        
        if not recent_times or not recent_successes:
            return 1.0
        
        # Calculate average response time penalty (normalized)
        avg_time = sum(recent_times) / len(recent_times)
        time_score = max(0.0, 1.0 - (avg_time / 10000))  # Penalty after 10s
        
        # Calculate success rate
        success_rate = sum(recent_successes) / len(recent_successes)
        
        # Combined health score
        health_score = (time_score * 0.3) + (success_rate * 0.7)
        
        return max(0.1, min(1.0, health_score))


class ModelRouter:
    """Intelligent AI model router with advanced selection algorithms."""
    
    def __init__(self):
        self.models = self._initialize_models()
        self.load_balancer = LoadBalancer()
        self.routing_cache = {}  # Cache routing decisions for similar requests
        self.performance_metrics = self._initialize_metrics()
        
        # Model selection strategies
        self.strategies = {
            "cost_optimized": self._cost_optimized_selection,
            "performance_optimized": self._performance_optimized_selection,
            "balanced": self._balanced_selection,
            "creative_optimized": self._creative_optimized_selection,
            "technical_optimized": self._technical_optimized_selection
        }
    
    def _initialize_models(self) -> Dict[str, ModelConfig]:
        """Initialize available models with their configurations."""
        return {
            # OpenAI Models
            "gpt-4o": ModelConfig(
                name="gpt-4o",
                provider=ModelProvider.OPENAI,
                capabilities=[
                    ModelCapability.TECHNICAL_WRITING,
                    ModelCapability.ANALYTICAL_REASONING,
                    ModelCapability.CODE_UNDERSTANDING,
                    ModelCapability.LONG_CONTEXT
                ],
                cost_per_token=0.00003,
                avg_response_time_ms=2500,
                context_window=128000,
                output_token_limit=4096,
                quality_score=0.95,
                max_concurrent_requests=15
            ),
            "gpt-4-turbo": ModelConfig(
                name="gpt-4-turbo",
                provider=ModelProvider.OPENAI,
                capabilities=[
                    ModelCapability.TECHNICAL_WRITING,
                    ModelCapability.CREATIVE_WRITING,
                    ModelCapability.ANALYTICAL_REASONING,
                    ModelCapability.LONG_CONTEXT
                ],
                cost_per_token=0.00001,
                avg_response_time_ms=3000,
                context_window=128000,
                output_token_limit=4096,
                quality_score=0.90,
                max_concurrent_requests=12
            ),
            "gpt-3.5-turbo": ModelConfig(
                name="gpt-3.5-turbo",
                provider=ModelProvider.OPENAI,
                capabilities=[
                    ModelCapability.TECHNICAL_WRITING,
                    ModelCapability.FAST_RESPONSE,
                    ModelCapability.COST_EFFECTIVE
                ],
                cost_per_token=0.0000015,
                avg_response_time_ms=1500,
                context_window=16385,
                output_token_limit=4096,
                quality_score=0.75,
                max_concurrent_requests=20
            ),
            
            # Anthropic Models
            "claude-3-5-sonnet-20241022": ModelConfig(
                name="claude-3-5-sonnet-20241022",
                provider=ModelProvider.ANTHROPIC,
                capabilities=[
                    ModelCapability.CREATIVE_WRITING,
                    ModelCapability.ANALYTICAL_REASONING,
                    ModelCapability.LONG_CONTEXT,
                    ModelCapability.CODE_UNDERSTANDING
                ],
                cost_per_token=0.000015,
                avg_response_time_ms=2800,
                context_window=200000,
                output_token_limit=8192,
                quality_score=0.92,
                max_concurrent_requests=10
            ),
            "claude-3-sonnet-20240229": ModelConfig(
                name="claude-3-sonnet-20240229",
                provider=ModelProvider.ANTHROPIC,
                capabilities=[
                    ModelCapability.CREATIVE_WRITING,
                    ModelCapability.ANALYTICAL_REASONING,
                    ModelCapability.LONG_CONTEXT
                ],
                cost_per_token=0.000015,
                avg_response_time_ms=3200,
                context_window=200000,
                output_token_limit=4096,
                quality_score=0.88,
                max_concurrent_requests=8
            ),
            "claude-3-haiku-20240307": ModelConfig(
                name="claude-3-haiku-20240307",
                provider=ModelProvider.ANTHROPIC,
                capabilities=[
                    ModelCapability.FAST_RESPONSE,
                    ModelCapability.COST_EFFECTIVE,
                    ModelCapability.TECHNICAL_WRITING
                ],
                cost_per_token=0.00000025,
                avg_response_time_ms=800,
                context_window=200000,
                output_token_limit=4096,
                quality_score=0.82,
                max_concurrent_requests=25
            )
        }
    
    def _initialize_metrics(self) -> Dict[str, Any]:
        """Initialize Prometheus metrics for monitoring."""
        return {
            "routing_requests": Counter("model_routing_requests_total", 
                                      "Total model routing requests", 
                                      ["strategy", "selected_model"]),
            "routing_latency": Histogram("model_routing_latency_seconds",
                                       "Model routing decision time",
                                       ["strategy"]),
            "model_utilization": Gauge("model_utilization_ratio",
                                     "Model utilization ratio",
                                     ["model_name", "provider"]),
            "routing_cache_hits": Counter("routing_cache_hits_total",
                                        "Routing cache hits"),
            "routing_cache_misses": Counter("routing_cache_misses_total",
                                          "Routing cache misses")
        }
    
    async def route_request(self, request: RoutingRequest) -> RoutingResult:
        """Main routing method with intelligent model selection."""
        start_time = time.time()
        
        try:
            # Check routing cache first
            cache_key = self._generate_cache_key(request)
            if cache_key in self.routing_cache:
                cached_result = self.routing_cache[cache_key]
                # Validate cached model is still available
                if self._is_model_available(cached_result.model_name):
                    self.performance_metrics["routing_cache_hits"].inc()
                    return cached_result
            
            self.performance_metrics["routing_cache_misses"].inc()
            
            # Determine routing strategy based on request
            strategy = self._determine_strategy(request)
            
            # Apply routing strategy
            result = await self.strategies[strategy](request)
            
            # Cache the result
            self.routing_cache[cache_key] = result
            
            # Update metrics
            self.performance_metrics["routing_requests"].labels(
                strategy=strategy, 
                selected_model=result.model_name
            ).inc()
            
            routing_time = time.time() - start_time
            self.performance_metrics["routing_latency"].labels(strategy=strategy).observe(routing_time)
            
            logger.info(f"Model routed: {result.model_name} via {strategy} strategy "
                       f"(confidence: {result.confidence_score:.3f}, cost: ${result.estimated_cost:.4f})")
            
            return result
            
        except Exception as e:
            logger.error(f"Model routing failed: {e}")
            # Return fallback result
            return await self._fallback_routing(request)
    
    def _generate_cache_key(self, request: RoutingRequest) -> str:
        """Generate cache key for routing request."""
        # Create hash of relevant request parameters
        key_data = {
            "jd_hash": hashlib.md5(request.job_description.encode()).hexdigest()[:8],
            "complexity": request.complexity_level,
            "capabilities": sorted([c.value for c in request.required_capabilities]),
            "max_cost": request.max_cost_per_request,
            "max_time": request.max_response_time_ms,
            "quality_threshold": request.quality_threshold
        }
        
        key_str = json.dumps(key_data, sort_keys=True)
        return hashlib.sha256(key_str.encode()).hexdigest()[:16]
    
    def _determine_strategy(self, request: RoutingRequest) -> str:
        """Determine the best routing strategy for the request."""
        # Check user preferences
        if request.user_preferences.get("optimize_for") == "cost":
            return "cost_optimized"
        elif request.user_preferences.get("optimize_for") == "performance":
            return "performance_optimized"
        elif request.user_preferences.get("optimize_for") == "creativity":
            return "creative_optimized"
        
        # Check required capabilities
        if ModelCapability.TECHNICAL_WRITING in request.required_capabilities:
            return "technical_optimized"
        elif ModelCapability.CREATIVE_WRITING in request.required_capabilities:
            return "creative_optimized"
        
        # Check constraints
        if request.max_cost_per_request and request.max_cost_per_request < 0.01:
            return "cost_optimized"
        elif request.max_response_time_ms and request.max_response_time_ms < 2000:
            return "performance_optimized"
        
        # Default to balanced approach
        return "balanced"
    
    async def _cost_optimized_selection(self, request: RoutingRequest) -> RoutingResult:
        """Select model optimized for cost while meeting requirements."""
        candidates = self._filter_candidates(request)
        
        # Sort by cost per token (ascending)
        candidates.sort(key=lambda m: m.cost_per_token)
        
        # Select the cheapest model that meets quality threshold
        selected = None
        for model in candidates:
            if model.quality_score >= request.quality_threshold:
                selected = self.load_balancer.select_least_loaded_model([model])
                if selected:
                    break
        
        if not selected:
            # Fallback to cheapest available
            selected = self.load_balancer.select_least_loaded_model(candidates)
        
        if not selected:
            raise Exception("No models available for cost-optimized routing")
        
        estimated_cost = self._estimate_cost(request.job_description, selected)
        
        return RoutingResult(
            provider=selected.provider,
            model_name=selected.name,
            config=selected,
            selection_reason=f"Cost-optimized: ${selected.cost_per_token:.6f}/token",
            confidence_score=0.9,
            estimated_cost=estimated_cost,
            estimated_time_ms=selected.avg_response_time_ms,
            fallback_models=[(m.provider, m.name) for m in candidates[1:3]]
        )
    
    async def _performance_optimized_selection(self, request: RoutingRequest) -> RoutingResult:
        """Select model optimized for speed and performance."""
        candidates = self._filter_candidates(request)
        
        # Sort by average response time (ascending)
        candidates.sort(key=lambda m: m.avg_response_time_ms)
        
        # Select fastest model that meets requirements
        selected = None
        for model in candidates:
            if (model.quality_score >= request.quality_threshold and
                all(cap in model.capabilities for cap in request.required_capabilities)):
                selected = self.load_balancer.select_least_loaded_model([model])
                if selected:
                    break
        
        if not selected:
            selected = self.load_balancer.select_least_loaded_model(candidates)
        
        if not selected:
            raise Exception("No models available for performance-optimized routing")
        
        estimated_cost = self._estimate_cost(request.job_description, selected)
        
        return RoutingResult(
            provider=selected.provider,
            model_name=selected.name,
            config=selected,
            selection_reason=f"Performance-optimized: {selected.avg_response_time_ms}ms avg response",
            confidence_score=0.85,
            estimated_cost=estimated_cost,
            estimated_time_ms=selected.avg_response_time_ms,
            fallback_models=[(m.provider, m.name) for m in candidates[1:3]]
        )
    
    async def _balanced_selection(self, request: RoutingRequest) -> RoutingResult:
        """Select model with best balance of cost, performance, and quality."""
        candidates = self._filter_candidates(request)
        
        # Calculate composite score for each model
        scored_models = []
        for model in candidates:
            # Normalize metrics (0-1 scale, higher is better)
            cost_score = 1.0 - min(model.cost_per_token / 0.00003, 1.0)  # Normalize against GPT-4 cost
            time_score = 1.0 - min(model.avg_response_time_ms / 5000, 1.0)  # Normalize against 5s
            quality_score = model.quality_score
            health_score = self.load_balancer.get_model_health_score(model.name)
            
            # Weighted composite score
            composite_score = (
                cost_score * 0.25 +
                time_score * 0.25 +
                quality_score * 0.35 +
                health_score * 0.15
            )
            
            scored_models.append((model, composite_score))
        
        # Sort by composite score (descending)
        scored_models.sort(key=lambda x: x[1], reverse=True)
        
        # Select highest scoring available model
        selected = None
        selected_score = 0
        for model, score in scored_models:
            candidate = self.load_balancer.select_least_loaded_model([model])
            if candidate:
                selected = candidate
                selected_score = score
                break
        
        if not selected:
            raise Exception("No models available for balanced routing")
        
        estimated_cost = self._estimate_cost(request.job_description, selected)
        
        return RoutingResult(
            provider=selected.provider,
            model_name=selected.name,
            config=selected,
            selection_reason=f"Balanced selection: {selected_score:.3f} composite score",
            confidence_score=selected_score,
            estimated_cost=estimated_cost,
            estimated_time_ms=selected.avg_response_time_ms,
            fallback_models=[(m.provider, m.name) for m, _ in scored_models[1:3]]
        )
    
    async def _creative_optimized_selection(self, request: RoutingRequest) -> RoutingResult:
        """Select model optimized for creative writing tasks."""
        candidates = self._filter_candidates(request)
        
        # Filter for models with creative writing capability
        creative_models = [m for m in candidates 
                          if ModelCapability.CREATIVE_WRITING in m.capabilities]
        
        if not creative_models:
            creative_models = candidates  # Fallback to all candidates
        
        # Prefer Anthropic models for creative tasks, then by quality
        creative_models.sort(key=lambda m: (
            m.provider != ModelProvider.ANTHROPIC,  # Anthropic first
            -m.quality_score  # Then by quality (descending)
        ))
        
        selected = self.load_balancer.select_least_loaded_model(creative_models)
        
        if not selected:
            raise Exception("No models available for creative-optimized routing")
        
        estimated_cost = self._estimate_cost(request.job_description, selected)
        
        return RoutingResult(
            provider=selected.provider,
            model_name=selected.name,
            config=selected,
            selection_reason="Creative-optimized: Anthropic model preferred for creative tasks",
            confidence_score=0.88,
            estimated_cost=estimated_cost,
            estimated_time_ms=selected.avg_response_time_ms,
            fallback_models=[(m.provider, m.name) for m in creative_models[1:3]]
        )
    
    async def _technical_optimized_selection(self, request: RoutingRequest) -> RoutingResult:
        """Select model optimized for technical writing tasks."""
        candidates = self._filter_candidates(request)
        
        # Filter for models with technical writing capability
        technical_models = [m for m in candidates 
                           if ModelCapability.TECHNICAL_WRITING in m.capabilities]
        
        if not technical_models:
            technical_models = candidates  # Fallback to all candidates
        
        # Prefer OpenAI models for technical tasks, then by quality
        technical_models.sort(key=lambda m: (
            m.provider != ModelProvider.OPENAI,  # OpenAI first
            -m.quality_score  # Then by quality (descending)
        ))
        
        selected = self.load_balancer.select_least_loaded_model(technical_models)
        
        if not selected:
            raise Exception("No models available for technical-optimized routing")
        
        estimated_cost = self._estimate_cost(request.job_description, selected)
        
        return RoutingResult(
            provider=selected.provider,
            model_name=selected.name,
            config=selected,
            selection_reason="Technical-optimized: OpenAI model preferred for technical content",
            confidence_score=0.90,
            estimated_cost=estimated_cost,
            estimated_time_ms=selected.avg_response_time_ms,
            fallback_models=[(m.provider, m.name) for m in technical_models[1:3]]
        )
    
    def _filter_candidates(self, request: RoutingRequest) -> List[ModelConfig]:
        """Filter models based on request constraints."""
        candidates = [m for m in self.models.values() if m.enabled]
        
        # Filter by required capabilities
        if request.required_capabilities:
            candidates = [m for m in candidates 
                         if any(cap in m.capabilities for cap in request.required_capabilities)]
        
        # Filter by cost constraint
        if request.max_cost_per_request:
            candidates = [m for m in candidates 
                         if self._estimate_cost(request.job_description, m) <= request.max_cost_per_request]
        
        # Filter by time constraint
        if request.max_response_time_ms:
            candidates = [m for m in candidates 
                         if m.avg_response_time_ms <= request.max_response_time_ms]
        
        # Filter by quality threshold
        candidates = [m for m in candidates 
                     if m.quality_score >= request.quality_threshold]
        
        return candidates
    
    def _estimate_cost(self, text: str, model: ModelConfig) -> float:
        """Estimate cost for processing the given text with the model."""
        # Rough token estimation (4 chars per token average)
        estimated_tokens = len(text) / 4
        
        # Add output tokens estimate (cover letters typically 200-400 tokens)
        output_tokens = 300
        
        total_tokens = estimated_tokens + output_tokens
        return total_tokens * model.cost_per_token
    
    def _is_model_available(self, model_name: str) -> bool:
        """Check if a model is currently available."""
        if model_name not in self.models:
            return False
        
        model = self.models[model_name]
        return (model.enabled and 
                model.current_load < model.max_concurrent_requests and
                model.availability_score > 0.1)
    
    async def _fallback_routing(self, request: RoutingRequest) -> RoutingResult:
        """Provide fallback routing when primary routing fails."""
        # Try to find any available model
        available_models = [m for m in self.models.values() 
                           if self._is_model_available(m.name)]
        
        if not available_models:
            # Ultimate fallback - return GPT-3.5 Turbo configuration
            fallback_model = self.models.get("gpt-3.5-turbo")
            if not fallback_model:
                raise Exception("No fallback models available")
            
            return RoutingResult(
                provider=fallback_model.provider,
                model_name=fallback_model.name,
                config=fallback_model,
                selection_reason="Emergency fallback - no models available",
                confidence_score=0.1,
                estimated_cost=self._estimate_cost(request.job_description, fallback_model),
                estimated_time_ms=fallback_model.avg_response_time_ms,
                fallback_models=[]
            )
        
        # Select first available model
        selected = available_models[0]
        estimated_cost = self._estimate_cost(request.job_description, selected)
        
        return RoutingResult(
            provider=selected.provider,
            model_name=selected.name,
            config=selected,
            selection_reason="Fallback routing - first available model",
            confidence_score=0.5,
            estimated_cost=estimated_cost,
            estimated_time_ms=selected.avg_response_time_ms,
            fallback_models=[(m.provider, m.name) for m in available_models[1:3]]
        )
    
    def update_model_load(self, model_name: str, load_change: int):
        """Update current load for a model."""
        if model_name in self.models:
            self.models[model_name].current_load = max(0, 
                self.models[model_name].current_load + load_change)
            
            # Update metrics
            utilization = (self.models[model_name].current_load / 
                          max(self.models[model_name].max_concurrent_requests, 1))
            self.performance_metrics["model_utilization"].labels(
                model_name=model_name,
                provider=self.models[model_name].provider.value
            ).set(utilization)
    
    def update_model_performance(self, model_name: str, response_time_ms: float, 
                               success: bool, cost: float):
        """Update model performance metrics."""
        self.load_balancer.update_model_performance(model_name, response_time_ms, success)
        
        # Update availability score based on recent performance
        if model_name in self.models:
            health_score = self.load_balancer.get_model_health_score(model_name)
            self.models[model_name].availability_score = health_score
    
    def get_routing_statistics(self) -> Dict[str, Any]:
        """Get routing statistics and performance metrics."""
        stats = {
            "total_models": len(self.models),
            "enabled_models": len([m for m in self.models.values() if m.enabled]),
            "cache_size": len(self.routing_cache),
            "models": {}
        }
        
        for name, model in self.models.items():
            stats["models"][name] = {
                "provider": model.provider.value,
                "current_load": model.current_load,
                "utilization": model.current_load / max(model.max_concurrent_requests, 1),
                "availability_score": model.availability_score,
                "health_score": self.load_balancer.get_model_health_score(name),
                "avg_response_time": model.avg_response_time_ms,
                "quality_score": model.quality_score
            }
        
        return stats


# Global router instance
model_router = ModelRouter()