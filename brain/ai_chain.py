# This module contains the optimized LangChain logic with caching, streaming, and intelligent model selection.

import asyncio
import logging
import os
import time
from typing import AsyncGenerator, Dict, Optional, Tuple, Any

from langchain_core.language_models import BaseLanguageModel
from langchain_core.output_parsers import JsonOutputParser, StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import RunnablePassthrough
from langchain_core.runnables.base import Runnable
from langchain_openai import ChatOpenAI

from exceptions import WebScrapingException
from semantic_cache import get_semantic_cache, CacheEntry
# from ai_optimizer import get_ai_optimizer, OptimizationProfile, TaskComplexity, RequestMetrics
from streaming_handler import get_streaming_handler

# Import advanced model router for performance tracking
try:
    from model_router import model_router
    MODEL_ROUTER_AVAILABLE = True
except ImportError:
    MODEL_ROUTER_AVAILABLE = False
    model_router = None

# Import streaming AI chain for enhanced performance
try:
    from streaming_ai_chain import create_streaming_cover_letter_chain
    STREAMING_AI_AVAILABLE = True
except ImportError:
    STREAMING_AI_AVAILABLE = False

try:
    from langchain_anthropic import ChatAnthropic

    ANTHROPIC_AVAILABLE = True
except ImportError:
    ANTHROPIC_AVAILABLE = False


def create_llm(
    model_provider: str = "openai", model_name: Optional[str] = None, temperature: float = 0.7
) -> BaseLanguageModel:
    """
    Create an LLM instance based on the specified provider and model.

    Args:
        model_provider: The AI provider to use ("openai" or "anthropic")
        model_name: Specific model name to use (optional)
        temperature: Temperature setting for the model

    Returns:
        LangChain LLM instance

    Raises:
        ValueError: If provider is not supported or required API key is missing
    """
    if model_provider.lower() == "openai":
        if not os.getenv("OPENAI_API_KEY"):
            raise ValueError("OpenAI API key not found. Set OPENAI_API_KEY environment variable.")

        model = model_name or "gpt-4o"
        return ChatOpenAI(model=model, temperature=temperature)

    elif model_provider.lower() == "anthropic":
        if not ANTHROPIC_AVAILABLE:
            raise ValueError(
                "Anthropic integration not available. Install langchain-anthropic package."
            )

        if not os.getenv("ANTHROPIC_API_KEY"):
            raise ValueError(
                "Anthropic API key not found. Set ANTHROPIC_API_KEY environment variable."
            )

        model = model_name or "claude-3-5-sonnet-20241022"
        return ChatAnthropic(model_name=model, temperature=temperature, timeout=60, stop=[])

    else:
        raise ValueError(
            f"Unsupported model provider: {model_provider}. Supported providers: openai, anthropic"
        )


async def create_optimized_streaming_cover_letter_chain(
    jd_text: str,
    model_provider: Optional[str] = None,
    model_name: Optional[str] = None,
    user_id: Optional[str] = None,
    job_id: Optional[str] = None,
    optimization_profile: str = "balanced",
    enable_streaming: bool = True,
    enable_caching: bool = True
):
    """
    Enhanced cover letter generation with native streaming support.
    
    This function provides real-time token-by-token streaming from LLMs,
    significantly improving perceived performance and user experience.
    Falls back to the standard optimized chain if streaming is not available.
    
    Args:
        jd_text: Job description text to process
        model_provider: AI provider ("openai" or "anthropic")
        model_name: Specific model name to use
        user_id: User identifier for analytics  
        job_id: Job identifier for tracking
        optimization_profile: Optimization strategy
        enable_streaming: Whether to enable streaming responses
        enable_caching: Whether to use semantic caching
        
    Returns:
        AsyncGenerator yielding streaming updates or standard response
    """
    logger = logging.getLogger(__name__)
    
    # Use streaming AI chain if available and streaming is enabled
    if STREAMING_AI_AVAILABLE and enable_streaming:
        try:
            logger.info(f"Using streaming AI chain for job {job_id}")
            async for update in create_streaming_cover_letter_chain(
                jd_text=jd_text,
                model_provider=model_provider,
                model_name=model_name,
                user_id=user_id,
                job_id=job_id,
                optimization_profile=optimization_profile,
                enable_streaming=enable_streaming,
                enable_caching=enable_caching
            ):
                yield update
            return
        except Exception as e:
            logger.warning(f"Streaming AI chain failed for job {job_id}: {e}. Falling back to standard chain.")
    
    # Fallback to standard optimized chain
    logger.info(f"Using standard optimized chain for job {job_id}")
    
    try:
        content, metadata = await create_optimized_cover_letter_chain(
            jd_text=jd_text,
            model_provider=model_provider,
            model_name=model_name,
            user_id=user_id,
            job_id=job_id,
            optimization_profile=optimization_profile,
            enable_streaming=False,  # Standard chain doesn't stream
            enable_caching=enable_caching
        )
        
        # Yield complete response in streaming format for consistency
        yield {
            "job_id": job_id,
            "phase": "completed",
            "progress": 1.0,
            "content": content,
            "partial": False,
            "streaming": False,
            "complete": True,
            "fallback_to_standard": True,
            "timestamp": time.time(),
            **metadata  # Include all metadata from standard chain
        }
        
    except Exception as e:
        logger.error(f"Both streaming and standard chains failed for job {job_id}: {e}")
        yield {
            "job_id": job_id,
            "phase": "error",
            "progress": 0.0,
            "error": str(e),
            "streaming": False,
            "timestamp": time.time()
        }


async def create_optimized_cover_letter_chain(
    jd_text: str,
    model_provider: Optional[str] = None,
    model_name: Optional[str] = None,
    user_id: Optional[str] = None,
    job_id: Optional[str] = None,
    optimization_profile: str = "balanced",
    enable_streaming: bool = True,
    enable_caching: bool = True
) -> Tuple[str, Dict[str, Any]]:
    """
    Creates an optimized cover letter generation pipeline with intelligent caching,
    model selection, streaming, and comprehensive cost optimization.

    This function integrates:
    - Semantic caching for similar job descriptions
    - Intelligent model selection based on task complexity
    - Streaming responses for improved perceived performance
    - Cost optimization and token usage tracking
    - Circuit breaker patterns for reliability

    Args:
        jd_text: Job description text to process
        model_provider: Preferred AI provider ("openai" or "anthropic")
        model_name: Specific model name to use (optional)
        user_id: User identifier for analytics and personalization
        job_id: Job identifier for tracking and caching
        optimization_profile: Optimization strategy ("quality_focused", "balanced", "cost_optimized", "speed_optimized")
        enable_streaming: Whether to enable streaming responses
        enable_caching: Whether to use semantic caching

    Returns:
        Tuple of (cover_letter_content, processing_metadata)
    """
    logger = logging.getLogger(__name__)
    start_time = time.time()
    
    # Initialize optimization components
    semantic_cache = get_semantic_cache() if enable_caching else None
    ai_optimizer = get_ai_optimizer()
    streaming_handler = get_streaming_handler() if enable_streaming else None
    
    # Initialize request metrics
    metrics = RequestMetrics(
        job_id=job_id or "unknown",
        user_id=user_id,
        streaming_enabled=enable_streaming
    )
    
    try:
        # Step 1: Parse job description first to get structured data
        logger.info(f"Starting optimized cover letter generation for job {job_id}")
        
        parsed_jd = await _parse_job_description_optimized(jd_text, ai_optimizer)
        logger.info(f"Parsed JD: company={parsed_jd.get('company')}, role={parsed_jd.get('role')}")
        
        # Step 2: Check semantic cache first
        cached_response = None
        if semantic_cache:
            logger.info("Checking semantic cache for similar job descriptions...")
            cached_response = await semantic_cache.get_cached_response(
                jd_text, model_provider or "openai", model_name or "auto", parsed_jd
            )
            
            if cached_response:
                logger.info(f"Cache hit! Using cached response with {cached_response.quality_score:.2f} quality score")
                metrics.cache_hit = True
                metrics.cost_usd = 0.0  # No cost for cached response
                metrics.quality_score = cached_response.quality_score
                
                # Record metrics and return cached content
                ai_optimizer.record_request_metrics(metrics)
                
                return cached_response.content, {
                    "cached": True,
                    "quality_score": cached_response.quality_score,
                    "original_cost_usd": cached_response.cost_usd,
                    "processing_time_ms": int((time.time() - start_time) * 1000),
                    "model_used": f"{cached_response.model_provider}:{cached_response.model_name}",
                    "cache_stats": semantic_cache.get_cache_stats()
                }
        
        # Step 3: Assess task complexity for intelligent model selection
        complexity = ai_optimizer.assess_task_complexity(jd_text, parsed_jd)
        logger.info(f"Task complexity assessed as: {complexity.value}")
        
        # Step 4: Get optimization profile
        profile = ai_optimizer.profiles.get(optimization_profile, ai_optimizer.profiles["balanced"])
        
        # Step 5: Select optimal model
        if not model_provider or not model_name:
            selected_provider, selected_model = ai_optimizer.select_optimal_model(
                complexity, profile, user_id
            )
        else:
            selected_provider, selected_model = model_provider, model_name
        
        logger.info(f"Selected model: {selected_provider}:{selected_model}")
        metrics.provider = selected_provider
        metrics.model_name = selected_model
        
        # Step 6: Estimate cost before processing
        estimated_cost = ai_optimizer.estimate_cost(jd_text, selected_model)
        logger.info(f"Estimated cost: ${estimated_cost:.4f}")
        
        # Check if cost exceeds profile limits
        if estimated_cost > profile.max_cost_per_request:
            logger.warning(f"Estimated cost ${estimated_cost:.4f} exceeds limit ${profile.max_cost_per_request:.4f}")
            # Fall back to cheaper model
            if selected_model == "gpt-4o":
                selected_provider, selected_model = "openai", "gpt-3.5-turbo"
                estimated_cost = ai_optimizer.estimate_cost(jd_text, selected_model)
                logger.info(f"Fallback to {selected_model}, new estimated cost: ${estimated_cost:.4f}")
        
        # Step 7: Preload and warm up the model
        llm = await ai_optimizer.preload_model(selected_provider, selected_model)
        
        # Step 8: Optimize prompts for token efficiency
        parsing_prompt = ai_optimizer.optimize_prompt(_get_optimized_parsing_prompt())
        writing_prompt = ai_optimizer.optimize_prompt(_get_optimized_writing_prompt())
        
        # Step 9: Create the AI processing chain
        parsing_template = ChatPromptTemplate.from_template(parsing_prompt)
        writing_template = ChatPromptTemplate.from_template(writing_prompt)
        
        # Execute parsing with circuit breaker protection
        try:
            parsing_chain = parsing_template | llm | JsonOutputParser()
            enhanced_parsed_jd = await _execute_with_circuit_breaker(
                parsing_chain, {"jd_text": jd_text}, selected_model, ai_optimizer
            )
            
            # Merge with initial parsing
            parsed_jd.update(enhanced_parsed_jd)
            
        except Exception as e:
            logger.warning(f"Enhanced parsing failed, using basic parsing: {e}")
            ai_optimizer.record_model_failure(selected_model, e)
        
        # Step 10: Generate cover letter with streaming if enabled
        writing_chain = writing_template | llm | StrOutputParser()
        
        if enable_streaming and streaming_handler and hasattr(llm, 'astream'):
            logger.info("Generating cover letter with streaming...")
            cover_letter = await _generate_streaming_response(
                writing_chain, parsed_jd, streaming_handler, job_id, selected_model, ai_optimizer
            )
        else:
            logger.info("Generating complete cover letter...")
            cover_letter = await _execute_with_circuit_breaker(
                writing_chain, parsed_jd, selected_model, ai_optimizer
            )
        
        # Step 11: Calculate actual costs and quality
        input_tokens = ai_optimizer.count_tokens(jd_text)
        output_tokens = ai_optimizer.count_tokens(cover_letter)
        actual_cost = _calculate_actual_cost(selected_model, input_tokens, output_tokens, ai_optimizer)
        quality_score = _calculate_quality_score(cover_letter, parsed_jd)
        
        # Update metrics
        metrics.input_tokens = input_tokens
        metrics.output_tokens = output_tokens
        metrics.cost_usd = actual_cost
        metrics.quality_score = quality_score
        
        # Step 12: Cache the response for future use
        if semantic_cache and not cached_response:
            await semantic_cache.cache_response(
                jd_text, cover_letter, parsed_jd, selected_provider, selected_model,
                output_tokens, actual_cost
            )
            logger.info("Response cached for future use")
        
        # Step 13: Record success metrics
        ai_optimizer.record_model_success(selected_model)
        ai_optimizer.record_request_metrics(metrics)
        
        processing_metadata = {
            "cached": False,
            "model_used": f"{selected_provider}:{selected_model}",
            "complexity": complexity.value,
            "optimization_profile": optimization_profile,
            "cost_usd": actual_cost,
            "input_tokens": input_tokens,
            "output_tokens": output_tokens,
            "quality_score": quality_score,
            "processing_time_ms": int((time.time() - start_time) * 1000),
            "streaming_enabled": enable_streaming,
            "estimated_vs_actual_cost": {
                "estimated": estimated_cost,
                "actual": actual_cost,
                "difference": actual_cost - estimated_cost
            }
        }
        
        logger.info(f"Cover letter generated successfully in {processing_metadata['processing_time_ms']}ms")
        return cover_letter, processing_metadata
        
    except Exception as e:
        logger.error(f"Error in optimized cover letter generation: {e}")
        metrics.error_message = str(e)
        ai_optimizer.record_model_failure(metrics.model_name, e)
        ai_optimizer.record_request_metrics(metrics)
        
        # Return fallback response
        if streaming_handler:
            fallback = await streaming_handler.create_fallback_response(job_id or "unknown", str(e))
            return fallback["content"], {"error": str(e), "fallback": True}
        else:
            raise


def create_cover_letter_chain(
    model_provider: str = "openai", model_name: Optional[str] = None
) -> Runnable:
    """
    Legacy function for backward compatibility.
    Creates a basic LangChain chain without optimizations.

    For new implementations, use create_optimized_cover_letter_chain instead.
    
    Args:
        model_provider: The AI provider to use ("openai" or "anthropic")
        model_name: Specific model name to use (optional)

    Returns:
        A LangChain chain that takes input with 'jd_text' key and returns a cover letter string
    """
    logger = logging.getLogger(__name__)
    logger.warning("Using legacy cover letter chain - consider upgrading to optimized version")
    
    # A. Initialize the LLM based on provider selection
    llm = create_llm(model_provider, model_name, temperature=0.7)
    
    # Register LLM for memory tracking
    from resource_manager import register_ai_object
    register_ai_object(llm, cleanup_func=lambda: _cleanup_llm(llm))

    # B. Create a JD Parsing Chain
    parsing_prompt_template = ChatPromptTemplate.from_template(_get_optimized_parsing_prompt())
    parsing_chain = parsing_prompt_template | llm | JsonOutputParser()

    # C. Create a Cover Letter Writing Chain
    writing_prompt_template = ChatPromptTemplate.from_template(_get_optimized_writing_prompt())
    writing_chain = writing_prompt_template | llm | StrOutputParser()

    # D. Combine Chains into the Final Sequence
    chain = RunnablePassthrough.assign(parsed_jd=parsing_chain) | writing_chain
    
    # Register chain for memory tracking
    register_ai_object(chain, cleanup_func=lambda: _cleanup_chain(chain))

    # E. Return the Final Chain
    return chain


def _cleanup_llm(llm) -> None:
    """Cleanup function for LLM instances."""
    try:
        # Clear any cached data or connections
        if hasattr(llm, 'client') and hasattr(llm.client, 'close'):
            llm.client.close()
        if hasattr(llm, '_client') and hasattr(llm._client, 'close'):
            llm._client.close()
    except Exception as e:
        logger.warning(f"Error during LLM cleanup: {e}")


def _cleanup_chain(chain) -> None:
    """Cleanup function for LangChain chains."""
    try:
        # Clear chain components
        if hasattr(chain, 'steps'):
            chain.steps.clear()
    except Exception as e:
        logger.warning(f"Error during chain cleanup: {e}")


async def scrape_jd_text(url: str) -> str:
    """
    Scrapes job description text from a given URL using managed HTTP clients.

    Uses httpx with resource management and BeautifulSoup to fetch and parse web content.
    Implements retry logic and error handling for robustness.

    Args:
        url: The URL to scrape job description from

    Returns:
        str: Extracted job description text

    Raises:
        WebScrapingException: If scraping fails after retries
    """
    from bs4 import BeautifulSoup  # type: ignore
    from resource_manager import http_client

    logger = logging.getLogger(__name__)

    # Request headers
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.5",
        "Accept-Encoding": "gzip, deflate",
        "Connection": "keep-alive",
        "Upgrade-Insecure-Requests": "1",
    }

    max_retries = 3
    retry_delay = 2

    for attempt in range(max_retries):
        try:
            logger.info(f"Attempting to scrape URL: {url} (attempt {attempt + 1}/{max_retries})")

            # Use managed HTTP client
            async with http_client() as client:
                response = await client.get(url, headers=headers)
                response.raise_for_status()

                # Parse with BeautifulSoup
                soup = BeautifulSoup(response.content, "lxml")

                # Remove script and style elements
                for script in soup(["script", "style"]):
                    script.decompose()

                # Try common selectors for job descriptions
                job_content = None

                # Common job board selectors (LinkedIn, Indeed, etc.)
                selectors = [
                    '[data-testid="job-description"]',  # LinkedIn
                    ".jobsearch-jobDescriptionText",  # Indeed
                    ".job-description",  # Generic
                    ".description",  # Generic
                    ".job-details",  # Generic
                    ".job-content",  # Generic
                    "main",  # Fallback to main content
                    "article",  # Fallback to article
                ]

                for selector in selectors:
                    elements = soup.select(selector)
                    if elements:
                        job_content = elements[0]
                        logger.info(f"Found job content using selector: {selector}")
                        break

                if not job_content:
                    # Fallback: extract all text from body
                    job_content = soup.find("body")
                    logger.warning("Using fallback: extracting all body text")

                if job_content:
                    # Extract and clean text
                    text = job_content.get_text(separator=" ", strip=True)
                    # Clean up whitespace
                    text = " ".join(text.split())

                    # Validate we got meaningful content
                    if len(text) < 100:
                        raise Exception(
                            f"Extracted text too short ({len(text)} chars), may not be job description"
                        )

                    logger.info(f"Successfully scraped {len(text)} characters of job description")
                    return text
                else:
                    raise Exception("Could not find job description content")

        except Exception as e:
            logger.warning(f"Scraping error on attempt {attempt + 1}: {e}")

        # Wait before retry (except on last attempt)
        if attempt < max_retries - 1:
            logger.info(f"Retrying in {retry_delay} seconds...")
            await asyncio.sleep(retry_delay)  # Use async sleep
            retry_delay *= 2  # Exponential backoff

    # All retries failed - raise exception
    logger.error(f"Failed to scrape job description from {url} after {max_retries} attempts")
    raise WebScrapingException(
        f"Failed to scrape job description after {max_retries} attempts. Please verify the URL is accessible and contains a job description.",
        url=url,
        error_code="SCRAPING_FAILED",
        details={"max_retries": max_retries, "retry_delay": retry_delay},
    )


def scrape_jd_text_sync(url: str) -> str:
    """
    Synchronous wrapper for job description scraping.
    
    This function maintains backward compatibility while using the async implementation.
    """
    import asyncio
    
    try:
        loop = asyncio.get_event_loop()
        return loop.run_until_complete(scrape_jd_text(url))
    except RuntimeError:
        # No event loop running, create a new one
        return asyncio.run(scrape_jd_text(url))


# Helper functions for the optimized AI chain

async def _parse_job_description_optimized(jd_text: str, ai_optimizer) -> Dict[str, Any]:
    """Parse job description with basic extraction for initial analysis."""
    # Simple keyword-based parsing for initial classification
    text_lower = jd_text.lower()
    
    # Extract company name (basic heuristics)
    company = "unknown"
    company_indicators = ["at ", "join ", "company:", "about us"]
    for indicator in company_indicators:
        if indicator in text_lower:
            start = text_lower.find(indicator) + len(indicator)
            end = text_lower.find('\n', start)
            if end == -1:
                end = start + 50
            company = jd_text[start:end].strip().split()[0] if start < len(jd_text) else "unknown"
            break
    
    # Extract role (look for job titles)
    role = "unknown"
    role_indicators = ["position:", "role:", "job title:", "we are hiring"]
    for indicator in role_indicators:
        if indicator in text_lower:
            start = text_lower.find(indicator) + len(indicator)
            end = text_lower.find('\n', start)
            if end == -1:
                end = start + 50
            role = jd_text[start:end].strip() if start < len(jd_text) else "unknown"
            break
    
    # Extract skills (basic keyword matching)
    skill_keywords = [
        "python", "javascript", "react", "node.js", "aws", "docker", "kubernetes",
        "machine learning", "data science", "sql", "mongodb", "postgres",
        "communication", "leadership", "teamwork", "problem solving"
    ]
    
    skills = [skill for skill in skill_keywords if skill in text_lower][:5]
    
    return {
        "company": company,
        "role": role, 
        "skills": skills
    }


def _get_optimized_parsing_prompt() -> str:
    """Get optimized prompt for job description parsing."""
    return """Extract key information from this job description. Return JSON with company, role, skills (3-5 key requirements).

Job Description:
{jd_text}

JSON:"""


def _get_optimized_writing_prompt() -> str:
    """Get optimized prompt for cover letter writing."""
    return """Write a professional 200-400 word cover letter for:

Company: {company}
Role: {role}
Skills: {skills}

Structure:
1. Opening with role/company mention
2. Skills alignment: {skills}
3. Company enthusiasm
4. Call to action

Cover Letter:"""


async def _execute_with_circuit_breaker(chain, input_data, model_name: str, ai_optimizer):
    """Execute AI chain with circuit breaker protection."""
    try:
        if hasattr(chain, 'ainvoke'):
            result = await chain.ainvoke(input_data)
        else:
            # Fallback for sync chains
            result = await asyncio.get_event_loop().run_in_executor(
                None, chain.invoke, input_data
            )
        
        ai_optimizer.record_model_success(model_name)
        return result
        
    except Exception as e:
        ai_optimizer.record_model_failure(model_name, e)
        raise


async def _generate_streaming_response(
    writing_chain, parsed_jd: Dict[str, Any], streaming_handler, job_id: str, model_name: str, ai_optimizer
) -> str:
    """Generate cover letter with streaming support."""
    try:
        # Create an async generator from the chain
        async def chain_generator():
            if hasattr(writing_chain, 'astream'):
                async for chunk in writing_chain.astream(parsed_jd):
                    if hasattr(chunk, 'content'):
                        yield chunk.content
                    else:
                        yield str(chunk)
            else:
                # Fallback: get complete response and simulate streaming
                complete_response = await _execute_with_circuit_breaker(
                    writing_chain, parsed_jd, model_name, ai_optimizer
                )
                
                # Simulate streaming by yielding words
                words = complete_response.split()
                for i in range(0, len(words), 3):
                    chunk = " ".join(words[i:i+3]) + " "
                    yield chunk
                    await asyncio.sleep(0.05)  # Small delay
        
        # Collect streaming response
        complete_response = ""
        async for update in streaming_handler.stream_ai_response(
            job_id, chain_generator()
        ):
            if update.get("content"):
                complete_response += update["content"]
                if update.get("complete", False):
                    break
        
        return complete_response.strip()
        
    except Exception as e:
        logger = logging.getLogger(__name__)
        logger.error(f"Streaming generation failed: {e}")
        # Fallback to non-streaming
        return await _execute_with_circuit_breaker(
            writing_chain, parsed_jd, model_name, ai_optimizer
        )


def _calculate_actual_cost(model_name: str, input_tokens: int, output_tokens: int, ai_optimizer) -> float:
    """Calculate actual cost based on token usage."""
    if model_name not in ai_optimizer.models:
        return 0.0
    
    config = ai_optimizer.models[model_name]
    input_cost = (input_tokens / 1000.0) * config.cost_per_token_input
    output_cost = (output_tokens / 1000.0) * config.cost_per_token_output
    
    return input_cost + output_cost


def _calculate_quality_score(content: str, parsed_jd: Dict[str, Any]) -> float:
    """Calculate quality score for generated content."""
    score = 0.5  # Base score
    
    # Length check
    word_count = len(content.split())
    if 200 <= word_count <= 400:
        score += 0.3
    elif word_count >= 150:
        score += 0.2
    
    # Company mention
    company = parsed_jd.get("company", "").lower()
    if company and company != "unknown" and company in content.lower():
        score += 0.2
    
    # Skills mention
    skills = parsed_jd.get("skills", [])
    skills_mentioned = sum(1 for skill in skills if skill.lower() in content.lower())
    if skills_mentioned >= 3:
        score += 0.3
    elif skills_mentioned >= 1:
        score += 0.2
    
    # Structure check
    if "dear" in content.lower() or "hiring manager" in content.lower():
        score += 0.1
    
    if "sincerely" in content.lower() or "best regards" in content.lower():
        score += 0.1
    
    return min(1.0, max(0.0, score))
