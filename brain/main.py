import asyncio
import json
import logging
import logging.config
import os
import signal
import sys
import threading
import time
import uuid
from contextlib import asynccontextmanager
from typing import Any, AsyncGenerator, Dict, Optional

import httpx
import pika
from pika.adapters.blocking_connection import BlockingChannel
from pika.spec import Basic, BasicProperties
import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest
from opentelemetry import trace
from opentelemetry.exporter.jaeger.thrift import JaegerExporter
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
from opentelemetry.instrumentation.logging import LoggingInstrumentor
from opentelemetry.sdk.resources import SERVICE_NAME, SERVICE_VERSION, Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

from ai_chain import create_cover_letter_chain, create_optimized_cover_letter_chain, create_optimized_streaming_cover_letter_chain, scrape_jd_text_sync
from semantic_cache import initialize_cache
from vector_database import initialize_vector_database
from config.vector_cache_config import create_production_cache_config, validate_configuration
from ai_optimizer import get_ai_optimizer
from streaming_handler import configure_streaming
from config.optimization import get_optimization_config, validate_config
from advanced_cache_integration import initialize_unified_cache, get_integration_config, get_unified_cache
from exceptions import (
    AIProviderException,
    GatewayException,
    JobProcessingException,
    RabbitMQException,
    WebScrapingException,
)
from monitoring import monitor
from resource_manager import get_resource_manager, cleanup_ai_resources
from retry_utils import exponential_backoff_retry, notify_gateway_with_retry_sync
from tracing_utils import TraceContext, TracedLogger, create_trace_from_rabbitmq_properties
from grpc_server import JobProcessingServicer, create_grpc_server

# Load environment variables from .env file
load_dotenv()


def setup_opentelemetry() -> None:
    """Configure OpenTelemetry for the Brain service."""
    # Check if tracing is enabled
    tracing_enabled = os.getenv("TRACING_ENABLED", "true").lower() == "true"
    if not tracing_enabled:
        return

    # Configure the tracer provider with service information
    resource = Resource.create({
        SERVICE_NAME: "huskyapply-brain",
        SERVICE_VERSION: os.getenv("SERVICE_VERSION", "0.0.1"),
        "service.instance.id": os.getenv("HOSTNAME", f"brain-{int(time.time())}"),
        "environment": os.getenv("ENVIRONMENT", "development")
    })

    # Create tracer provider
    tracer_provider = TracerProvider(resource=resource)
    trace.set_tracer_provider(tracer_provider)

    # Configure exporter based on environment
    jaeger_endpoint = os.getenv("JAEGER_ENDPOINT", "http://localhost:14268/api/traces")
    sampling_probability = float(os.getenv("TRACING_SAMPLING_PROBABILITY", "0.1"))

    if "jaeger" in jaeger_endpoint:
        exporter = JaegerExporter(
            agent_host_name=jaeger_endpoint.split("//")[1].split(":")[0],
            agent_port=14268,
            collector_endpoint=jaeger_endpoint,
        )
    else:
        exporter = OTLPSpanExporter(endpoint=jaeger_endpoint)

    # Add batch span processor
    span_processor = BatchSpanProcessor(exporter)
    tracer_provider.add_span_processor(span_processor)

    # Auto-instrument HTTP client
    HTTPXClientInstrumentor().instrument()
    
    # Auto-instrument logging
    LoggingInstrumentor().instrument(set_logging_format=True)

    logging.info(f"OpenTelemetry configured with endpoint: {jaeger_endpoint}")


# Configure logging with structured format for traceability
class ContextFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        if not hasattr(record, "trace_id"):
            record.trace_id = "N/A"
        if not hasattr(record, "job_id"):
            record.job_id = "N/A"
        return True


logging.basicConfig(
    level=logging.INFO,
    format='level=%(levelname)s timestamp=%(asctime)s service=brain msg="%(message)s" trace_id=%(trace_id)s job_id=%(job_id)s',
)
logger = logging.getLogger(__name__)
logger.addFilter(ContextFilter())

# Service startup time
start_time = time.time()

# Gateway URL configuration
GATEWAY_INTERNAL_URL = os.getenv("GATEWAY_INTERNAL_URL", "http://localhost:8080")
INTERNAL_API_KEY = os.getenv("INTERNAL_API_KEY", "husky-internal-secret")

# Validate critical environment variables
required_env_vars = {
    "OPENAI_API_KEY": os.getenv("OPENAI_API_KEY"),
    "INTERNAL_API_KEY": INTERNAL_API_KEY,
}

missing_vars = [var for var, value in required_env_vars.items() if not value]
if missing_vars:
    logger.error(f"Missing required environment variables: {', '.join(missing_vars)}")
    sys.exit(1)

# Prometheus metrics
job_counter = Counter("jobs_processed_total", "Total number of jobs processed", ["status"])
job_duration = Histogram("job_processing_seconds", "Time spent processing jobs")
scraping_counter = Counter(
    "web_scraping_total", "Total number of web scraping attempts", ["status"]
)
scraping_duration = Histogram("web_scraping_seconds", "Time spent on web scraping")
ai_chain_duration = Histogram("ai_chain_seconds", "Time spent on AI chain processing")

# Global variable to track service shutdown
shutdown_event = threading.Event()


# Create FastAPI application with lifespan management
@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application lifespan manager with resource cleanup and optimization initialization."""
    # Startup
    logger.info("Starting HuskyApply Brain Service with AI Optimizations")
    
    # Load and validate optimization configuration
    optimization_config = get_optimization_config()
    config_issues = validate_config()
    
    if config_issues:
        logger.warning(f"Configuration issues detected: {config_issues}")
    
    logger.info(
        f"Service configuration: {{"
        + f"'gateway_url': '{GATEWAY_INTERNAL_URL}', "
        + f"'rabbitmq_host': '{os.getenv('RABBITMQ_HOST', 'localhost')}', "
        + f"'log_level': '{os.getenv('LOG_LEVEL', 'INFO')}', "
        + f"'caching_enabled': {optimization_config.enable_semantic_caching}, "
        + f"'streaming_enabled': {optimization_config.enable_streaming}, "
        + f"'optimization_profile': '{optimization_config.default_optimization_profile}'"
        + f"}}"
    )
    
    # Initialize resource manager
    resource_manager = get_resource_manager()
    logger.info("Resource manager initialized")
    
    # Initialize optimization components
    try:
        # Validate vector database configuration
        if not validate_configuration():
            logger.error("Vector database configuration validation failed")
            raise RuntimeError("Invalid vector cache configuration")
        
        # Initialize unified cache system (semantic + advanced multi-layer)
        if optimization_config.enable_semantic_caching:
            # Initialize advanced multi-layer cache with analytics
            cache_integration_config = get_integration_config()
            unified_cache = await initialize_unified_cache()
            
            logger.info(f"Unified cache system initialized: migration_mode={cache_integration_config.migration_mode}, "
                       f"L1_size={cache_integration_config.l1_cache_size}, "
                       f"analytics_enabled={cache_integration_config.enable_performance_monitoring}")
            
            # Initialize legacy semantic cache for backward compatibility
            enhanced_cache_config = create_production_cache_config()
            semantic_cache = await initialize_cache(enhanced_cache_config)
            logger.info("Legacy semantic cache maintained for backward compatibility")
        else:
            logger.info("Semantic caching disabled - AI costs will not be optimized")
        
        # Initialize AI optimizer
        ai_optimizer = get_ai_optimizer()
        logger.info("AI optimizer initialized with circuit breakers")
        
        # Initialize streaming handler if enabled
        if optimization_config.enable_streaming:
            streaming_config = optimization_config.to_streaming_config()
            streaming_handler = await configure_streaming(
                GATEWAY_INTERNAL_URL, INTERNAL_API_KEY, streaming_config
            )
            logger.info(f"Streaming handler initialized in {streaming_config.mode.value} mode")
        
        # Preload models if enabled
        if optimization_config.enable_model_preloading:
            try:
                await ai_optimizer.preload_model("openai", "gpt-4o")
                await ai_optimizer.preload_model("openai", "gpt-3.5-turbo")
                logger.info("Models preloaded and warmed up")
            except Exception as e:
                logger.warning(f"Model preloading failed: {e}")
        
        logger.info("All optimization components initialized successfully")
        
    except Exception as e:
        logger.error(f"Failed to initialize optimization components: {e}")
        # Continue without optimizations
        logger.warning("Continuing with basic functionality")
    
    yield
    
    # Shutdown
    logger.info("Shutting down HuskyApply Brain Service")
    shutdown_event.set()
    
    # Graceful resource cleanup
    try:
        # Shutdown unified cache system
        if optimization_config.enable_semantic_caching:
            try:
                unified_cache = await get_unified_cache()
                await unified_cache.shutdown()
                logger.info("Unified cache system shutdown completed")
            except Exception as e:
                logger.error(f"Error during cache shutdown: {e}")
        
        # Shutdown resource manager
        await resource_manager.shutdown()
        logger.info("Resource manager shutdown completed")
    except Exception as e:
        logger.error(f"Error during resource manager shutdown: {e}")


# Setup OpenTelemetry tracing
setup_opentelemetry()

app = FastAPI(
    title="HuskyApply Brain Service",
    version="0.0.1",
    description="AI-powered job application processing service",
    lifespan=lifespan,
)

# Instrument FastAPI app with OpenTelemetry
if os.getenv("TRACING_ENABLED", "true").lower() == "true":
    FastAPIInstrumentor.instrument_app(app)


@app.get("/healthz")
async def health_check() -> Dict[str, Any]:
    """Comprehensive health check endpoint with detailed status."""
    try:
        return await monitor.comprehensive_health_check()
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return {
            "status": "error",
            "timestamp": time.time(),
            "version": "0.0.1",
            "error": str(e),
            "message": "Health check system failure",
        }


@app.get("/metrics")
async def metrics() -> Response:
    """Prometheus metrics endpoint."""
    # Update system metrics before serving
    monitor.collect_system_metrics()
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.get("/status")
async def service_status() -> Dict[str, Any]:
    """Quick service status endpoint."""
    system_metrics = monitor.collect_system_metrics()
    return {
        "service": "HuskyApply Brain",
        "status": "running",
        "version": "0.0.1",
        "uptime_seconds": system_metrics.uptime_seconds,
        "timestamp": time.time(),
        "system": {
            "cpu_percent": system_metrics.cpu_percent,
            "memory_percent": system_metrics.memory_percent,
            "memory_used_mb": system_metrics.memory_used_mb,
        },
    }


@app.get("/optimization-status")
async def optimization_status() -> Dict[str, Any]:
    """Get status of AI optimization features."""
    try:
        optimization_config = get_optimization_config()
        
        # Get cache stats if available
        cache_stats = {}
        try:
            from semantic_cache import get_semantic_cache
            semantic_cache = get_semantic_cache()
            cache_stats = semantic_cache.get_cache_stats()
        except Exception as e:
            cache_stats = {"error": f"Cache unavailable: {str(e)}"}
        
        # Get AI optimizer stats
        ai_optimizer_stats = {}
        try:
            ai_optimizer = get_ai_optimizer()
            ai_optimizer_stats = ai_optimizer.get_cost_analytics()
        except Exception as e:
            ai_optimizer_stats = {"error": f"AI optimizer unavailable: {str(e)}"}
        
        # Get streaming stats
        streaming_stats = {}
        try:
            from streaming_handler import get_streaming_handler
            streaming_handler = get_streaming_handler()
            streaming_stats = streaming_handler.get_streaming_analytics()
        except Exception as e:
            streaming_stats = {"error": f"Streaming handler unavailable: {str(e)}"}
        
        return {
            "optimization_config": {
                "caching_enabled": optimization_config.enable_semantic_caching,
                "streaming_enabled": optimization_config.enable_streaming,
                "model_preloading_enabled": optimization_config.enable_model_preloading,
                "cost_tracking_enabled": optimization_config.cost_tracking_enabled,
                "default_profile": optimization_config.default_optimization_profile,
                "max_cost_per_request": optimization_config.max_cost_per_request
            },
            "cache_performance": cache_stats,
            "ai_cost_analytics": ai_optimizer_stats,
            "streaming_analytics": streaming_stats,
            "timestamp": time.time()
        }
        
    except Exception as e:
        logger.error(f"Error getting optimization status: {e}")
        return {"error": str(e), "timestamp": time.time()}


@app.get("/cost-analytics")
async def cost_analytics(user_id: Optional[str] = None) -> Dict[str, Any]:
    """Get detailed cost analytics for AI operations."""
    try:
        ai_optimizer = get_ai_optimizer()
        analytics = ai_optimizer.get_cost_analytics(user_id)
        
        return {
            "status": "success",
            "data": analytics,
            "timestamp": time.time()
        }
    except Exception as e:
        logger.error(f"Error getting cost analytics: {e}")
        return {"status": "error", "error": str(e), "timestamp": time.time()}


@app.get("/cache-analytics")
async def cache_analytics() -> Dict[str, Any]:
    """Get detailed cache performance analytics."""
    try:
        from semantic_cache import get_semantic_cache
        semantic_cache = get_semantic_cache()
        stats = semantic_cache.get_cache_stats()
        
        return {
            "status": "success",
            "data": stats,
            "timestamp": time.time()
        }
    except Exception as e:
        logger.error(f"Error getting cache analytics: {e}")
        return {"status": "error", "error": str(e), "timestamp": time.time()}


@app.post("/cache/clear")
async def clear_cache() -> Dict[str, Any]:
    """Clear the semantic cache."""
    try:
        from semantic_cache import get_semantic_cache
        semantic_cache = get_semantic_cache()
        success = await semantic_cache.clear_cache()
        
        return {
            "status": "success" if success else "error",
            "message": "Cache cleared successfully" if success else "Failed to clear cache",
            "timestamp": time.time()
        }
    except Exception as e:
        logger.error(f"Error clearing cache: {e}")
        return {"status": "error", "error": str(e), "timestamp": time.time()}


@app.get("/streaming-status")
async def streaming_status() -> Dict[str, Any]:
    """Get current streaming status and active streams."""
    try:
        from streaming_handler import get_streaming_handler
        streaming_handler = get_streaming_handler()
        
        return {
            "status": "success",
            "data": {
                "active_streams": streaming_handler.get_active_streams(),
                "analytics": streaming_handler.get_streaming_analytics()
            },
            "timestamp": time.time()
        }
    except Exception as e:
        logger.error(f"Error getting streaming status: {e}")
        return {"status": "error", "error": str(e), "timestamp": time.time()}


@app.post("/stream-test")
async def stream_test(request: Dict[str, Any]) -> Dict[str, Any]:
    """Test endpoint for streaming AI responses."""
    try:
        jd_text = request.get("jd_text", "Software Engineer position at a technology company.")
        model_provider = request.get("model_provider", "openai")
        model_name = request.get("model_name", "gpt-4o")
        job_id = request.get("job_id", f"stream_test_{int(time.time())}")
        
        logger.info(f"Starting streaming test for job {job_id}")
        
        # Collect all streaming updates
        updates = []
        async for update in create_optimized_streaming_cover_letter_chain(
            jd_text=jd_text,
            model_provider=model_provider,
            model_name=model_name,
            job_id=job_id,
            enable_streaming=True,
            enable_caching=False  # Disable cache for testing
        ):
            updates.append({
                "phase": update.get("phase"),
                "progress": update.get("progress", 0.0),
                "streaming": update.get("streaming", False),
                "partial": update.get("partial", False),
                "complete": update.get("complete", False),
                "content_preview": update.get("content", "")[:100] if update.get("content") else "",
                "tokens_generated": update.get("tokens_generated", 0),
                "quality_score": update.get("quality_score", 0.0),
                "timestamp": update.get("timestamp", time.time())
            })
        
        return {
            "status": "success",
            "job_id": job_id,
            "total_updates": len(updates),
            "streaming_updates": len([u for u in updates if u.get("streaming")]),
            "final_content_length": len(updates[-1].get("content_preview", "")) if updates else 0,
            "updates": updates,
            "timestamp": time.time()
        }
        
    except Exception as e:
        logger.error(f"Streaming test error: {e}")
        return {"status": "error", "error": str(e), "timestamp": time.time()}


@app.get("/model-status")
async def model_status() -> Dict[str, Any]:
    """Get status of AI models and circuit breakers."""
    try:
        ai_optimizer = get_ai_optimizer()
        
        return {
            "status": "success",
            "data": {
                "circuit_breakers": {
                    name: {
                        "state": cb.state,
                        "failure_count": cb.failure_count,
                        "last_failure_time": cb.last_failure_time,
                        "timeout_seconds": cb.timeout_seconds
                    }
                    for name, cb in ai_optimizer.circuit_breakers.items()
                },
                "preloaded_models": list(ai_optimizer.preloaded_models.keys()),
                "model_configurations": {
                    name: {
                        "provider": config.provider,
                        "tier": config.tier.value,
                        "enabled": config.enabled,
                        "quality_score": config.quality_score
                    }
                    for name, config in ai_optimizer.models.items()
                }
            },
            "timestamp": time.time()
        }
    except Exception as e:
        logger.error(f"Error getting model status: {e}")
        return {"status": "error", "error": str(e), "timestamp": time.time()}


class RabbitMQConsumer:
    def __init__(self) -> None:
        self.connection: Optional[pika.BlockingConnection] = None
        self.channel: Optional[BlockingChannel] = None
        self.resource_manager = get_resource_manager()

    def connect(self) -> bool:
        # Get RabbitMQ connection details from environment variables
        rabbitmq_host = os.getenv("RABBITMQ_HOST", "localhost")
        rabbitmq_user = os.getenv("RABBITMQ_USER", "husky")
        rabbitmq_password = os.getenv("RABBITMQ_PASSWORD", "husky")
        rabbitmq_queue = os.getenv("RABBITMQ_QUEUE", "jobs.queue")

        credentials = pika.PlainCredentials(rabbitmq_user, rabbitmq_password)
        connection_params = pika.ConnectionParameters(
            host=rabbitmq_host,
            credentials=credentials,
            heartbeat=600,  # 10 minutes heartbeat
            blocked_connection_timeout=300,  # 5 minutes timeout
            connection_attempts=3,
            retry_delay=5,
        )

        try:
            self.connection = pika.BlockingConnection(connection_params)
            self.channel = self.connection.channel()

            # Declare the durable queue to ensure it matches the publisher's queue
            if self.channel is not None:
                self.channel.queue_declare(queue=rabbitmq_queue, durable=True)

                # Set QoS to process one message at a time
                self.channel.basic_qos(prefetch_count=1)

            logger.info("Connected to RabbitMQ successfully")
            return True
        except Exception as e:
            logger.error(f"Failed to connect to RabbitMQ: {e}")
            return False

    def message_callback(
        self,
        channel: BlockingChannel,
        method: Basic.Deliver,
        properties: BasicProperties,
        body: bytes,
    ) -> None:
        # Start timing the job processing
        job_start_time = time.time()

        # Parse the incoming JSON message body first to get job_id
        try:
            message = json.loads(body.decode("utf-8"))
            job_id = message.get("jobId")
            jd_url = message.get("jdUrl")
            resume_uri = message.get("resumeUri")
            model_provider = message.get("modelProvider", "openai")
            model_name = message.get("modelName")
        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse JSON message: {e}")
            job_counter.labels(status="parse_error").inc()
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return

        # Create trace context for the entire job processing
        trace_ctx = create_trace_from_rabbitmq_properties(
            properties, job_id=job_id, operation="job_processing"
        )

        with trace_ctx:
            try:
                # 2. Immediately send PROCESSING status to Gateway
                processing_url = (
                    f"{GATEWAY_INTERNAL_URL}/api/v1/internal/applications/{job_id}/events"
                )
                processing_payload = {"status": "PROCESSING"}

                # Headers for internal API authentication (include tracing headers)
                internal_headers = {
                    "X-Internal-API-Key": INTERNAL_API_KEY,
                    "Content-Type": "application/json",
                }
                internal_headers.update(trace_ctx.get_headers())

                # Use retry mechanism for Gateway notification
                notify_gateway_with_retry_sync(
                    processing_url, job_id, processing_payload, internal_headers
                )

                # 3. Log that processing has started
                logger.info("Processing started", extra=trace_ctx.get_logging_extra())

                # 4. Scrape JD and get optimization configuration
                with trace_ctx.create_child_span("web_scraping") as scraping_span:
                    logger.info(
                        f"Scraping job description from: {jd_url}",
                        extra=scraping_span.get_logging_extra(),
                    )

                    # Time the scraping operation
                    scraping_start_time = time.time()
                    try:
                        jd_text = scrape_jd_text_sync(jd_url)  # Use sync wrapper
                        scraping_counter.labels(status="success").inc()
                    except Exception as scraping_error:
                        scraping_counter.labels(status="failure").inc()
                        raise scraping_error
                    finally:
                        scraping_duration.observe(time.time() - scraping_start_time)

                # 5. Use optimized AI chain with all enhancements
                with trace_ctx.create_child_span("ai_generation") as ai_span:
                    logger.info("Invoking optimized AI chain...", extra=ai_span.get_logging_extra())

                    # Time the AI chain processing
                    ai_start_time = time.time()
                    
                    try:
                        # Try streaming optimized chain first
                        try:
                            optimization_config = get_optimization_config()
                            
                            # Prepare callback URL for streaming
                            callback_url = f"{GATEWAY_INTERNAL_URL}/api/v1/internal/applications/{job_id}/events"
                            
                            # Configure streaming handler if not already configured
                            try:
                                from streaming_handler import get_streaming_handler
                                streaming_handler = get_streaming_handler()
                                if not streaming_handler.gateway_url:
                                    streaming_handler.configure_gateway(GATEWAY_INTERNAL_URL, INTERNAL_API_KEY)
                            except Exception as streaming_error:
                                logger.warning(f"Streaming handler configuration failed: {streaming_error}")
                            
                            # Use streaming AI chain for real-time updates
                            generated_cover_letter = None
                            processing_metadata = {}
                            
                            logger.info(f"Starting streaming AI generation for job {job_id}")
                            
                            # TODO: Fix async streaming - temporarily disabled for testing
                            # Using basic chain as fallback for GitHub deployment
                            logger.info("Using basic AI chain (streaming temporarily disabled)")
                            cover_letter_chain = create_cover_letter_chain(model_provider, model_name)
                            generated_cover_letter = cover_letter_chain.invoke({"jd_text": jd_text})
                            processing_metadata = {"streaming_disabled": True, "model_provider": model_provider, "model_name": model_name}
                            
                            logger.info(
                                f"Optimized AI processing completed. Cost: ${processing_metadata.get('cost_usd', 0):.4f}, "
                                f"Quality: {processing_metadata.get('quality_score', 0):.2f}, "
                                f"Cached: {processing_metadata.get('cached', False)}",
                                extra=trace_ctx.get_logging_extra(),
                            )
                            
                        except Exception as optimized_error:
                            logger.warning(f"Optimized chain failed, falling back to basic chain: {optimized_error}")
                            
                            # Fallback to basic chain
                            cover_letter_chain = create_cover_letter_chain(model_provider, model_name)
                            generated_cover_letter = cover_letter_chain.invoke({"jd_text": jd_text})
                            processing_metadata = {"fallback": True, "error": str(optimized_error)}
                            
                    finally:
                        ai_chain_duration.observe(time.time() - ai_start_time)

                # 6. Log the result snippet
                logger.info(
                    f"AI chain finished. Snippet: {generated_cover_letter[:70]}...",
                    extra=trace_ctx.get_logging_extra(),
                )

                # 7. Send COMPLETED status with generated content to Gateway
                completed_payload = {
                    "status": "COMPLETED", 
                    "content": generated_cover_letter,
                    "metadata": processing_metadata if 'processing_metadata' in locals() else {}
                }

                # Use retry mechanism for Gateway notification
                notify_gateway_with_retry_sync(
                    processing_url, job_id, completed_payload, internal_headers
                )

                # 8. Log that processing is complete
                logger.info("Processing completed", extra=trace_ctx.get_logging_extra())

                # Record successful job processing
                job_counter.labels(status="success").inc()
                job_duration.observe(time.time() - job_start_time)

                # 9. Cleanup AI resources after processing
                cleanup_ai_resources()
                
                # 10. Send acknowledgment to RabbitMQ
                channel.basic_ack(delivery_tag=method.delivery_tag)

            except Exception as e:
                logger.error("Error processing message", extra=trace_ctx.get_logging_extra())
                job_counter.labels(status="failure").inc()
                job_duration.observe(time.time() - job_start_time)

                # Send FAILED status to Gateway if we have a job_id
                if job_id:
                    try:
                        failed_url = (
                            f"{GATEWAY_INTERNAL_URL}/api/v1/internal/applications/{job_id}/events"
                        )
                        failed_payload = {"status": "FAILED"}
                        failed_headers = {
                            "X-Internal-API-Key": INTERNAL_API_KEY,
                            "Content-Type": "application/json",
                        }
                        failed_headers.update(trace_ctx.get_headers())

                        # Use retry mechanism for failure notification
                        notify_gateway_with_retry_sync(
                            failed_url, job_id, failed_payload, failed_headers
                        )

                        logger.info(
                            "Sent FAILED status to Gateway", extra=trace_ctx.get_logging_extra()
                        )
                    except Exception as notify_error:
                        logger.error(
                            f"Failed to notify Gateway of failure: {notify_error}",
                            extra=trace_ctx.get_logging_extra(),
                        )

                # Cleanup AI resources on failure as well
                cleanup_ai_resources()
                
                channel.basic_ack(delivery_tag=method.delivery_tag)

    def start_consuming(self) -> None:
        while True:
            try:
                if not self.connect():
                    logger.error("Failed to connect to RabbitMQ, retrying in 5 seconds...")
                    time.sleep(5)
                    continue

                rabbitmq_queue = os.getenv("RABBITMQ_QUEUE", "jobs.queue")

                if self.channel:
                    self.channel.basic_consume(
                        queue=rabbitmq_queue, on_message_callback=self.message_callback
                    )

                    logger.info("Starting to consume messages from jobs.queue")
                    self.channel.start_consuming()
                else:
                    raise Exception("Channel is not initialized")

            except KeyboardInterrupt:
                logger.info("Received interrupt signal, stopping consumer...")
                self.stop_consuming()
                break
            except Exception as e:
                logger.error(f"Consumer error: {e}")
                time.sleep(5)

    def stop_consuming(self) -> None:
        if self.channel:
            self.channel.stop_consuming()
        if self.connection and not self.connection.is_closed:
            self.connection.close()
        logger.info("RabbitMQ consumer stopped")


async def start_grpc_server_async():
    """Start the gRPC server asynchronously."""
    try:
        # Get gRPC configuration from environment
        grpc_enabled = os.getenv("GRPC_ENABLED", "true").lower() == "true"
        grpc_host = os.getenv("GRPC_HOST", "0.0.0.0")
        grpc_port = int(os.getenv("GRPC_PORT", "9090"))
        
        if not grpc_enabled:
            logger.info("gRPC server is disabled")
            return None, None
        
        # Create and start gRPC server
        server, servicer = await create_grpc_server(
            host=grpc_host, 
            port=grpc_port, 
            max_workers=int(os.getenv("GRPC_MAX_WORKERS", "10"))
        )
        
        # Initialize Gateway callback client if Gateway gRPC is enabled
        gateway_grpc_enabled = os.getenv("GATEWAY_GRPC_ENABLED", "true").lower() == "true"
        if gateway_grpc_enabled:
            gateway_host = os.getenv("GATEWAY_HOST", "localhost")
            gateway_grpc_port = int(os.getenv("GATEWAY_GRPC_PORT", "9091"))
            gateway_tls = os.getenv("GATEWAY_GRPC_TLS", "false").lower() == "true"
            
            await servicer.initialize_gateway_callback_client(
                gateway_host, gateway_grpc_port, gateway_tls
            )
        
        await server.start()
        logger.info(f"gRPC server started on {grpc_host}:{grpc_port}")
        
        return server, servicer
        
    except Exception as e:
        logger.error(f"Failed to start gRPC server: {e}")
        return None, None


async def run_hybrid_service():
    """Run both gRPC and FastAPI servers with RabbitMQ consumer."""
    shutdown_event = threading.Event()
    services = []
    
    try:
        # Start gRPC server
        grpc_server, grpc_servicer = await start_grpc_server_async()
        if grpc_server:
            services.append(("gRPC Server", grpc_server, grpc_servicer))
        
        # Start RabbitMQ consumer (if enabled)
        rabbitmq_enabled = os.getenv("RABBITMQ_ENABLED", "true").lower() == "true"
        if rabbitmq_enabled:
            consumer = RabbitMQConsumer()
            consumer_thread = threading.Thread(
                target=consumer.start_consuming, 
                daemon=True, 
                name="RabbitMQ-Consumer"
            )
            consumer_thread.start()
            services.append(("RabbitMQ Consumer", consumer_thread, consumer))
            logger.info("RabbitMQ consumer started")
        
        # Start FastAPI server
        uvicorn_config = uvicorn.Config(
            app, 
            host=os.getenv("FASTAPI_HOST", "0.0.0.0"), 
            port=int(os.getenv("FASTAPI_PORT", "8000")),
            log_level=os.getenv("LOG_LEVEL", "info").lower(),
            access_log=True,
            loop="asyncio"
        )
        
        fastapi_server = uvicorn.Server(uvicorn_config)
        services.append(("FastAPI Server", fastapi_server, None))
        
        # Signal handlers for graceful shutdown
        def signal_handler(signum, frame):
            logger.info(f"Received signal {signum}, initiating graceful shutdown...")
            shutdown_event.set()
        
        signal.signal(signal.SIGTERM, signal_handler)
        signal.signal(signal.SIGINT, signal_handler)
        
        logger.info("=== HuskyApply Brain Service Started ===")
        logger.info(f"Services running: {[name for name, _, _ in services]}")
        
        # Run FastAPI server
        await fastapi_server.serve()
        
    except KeyboardInterrupt:
        logger.info("Received interrupt signal")
        shutdown_event.set()
    except Exception as e:
        logger.error(f"Error running services: {e}")
        shutdown_event.set()
    finally:
        # Graceful shutdown of all services
        logger.info("Shutting down all services...")
        
        # Shutdown gRPC server
        if grpc_server and grpc_servicer:
            try:
                await grpc_servicer.shutdown()
                await grpc_server.stop(grace=10.0)
                logger.info("gRPC server stopped")
            except Exception as e:
                logger.error(f"Error stopping gRPC server: {e}")
        
        # Shutdown FastAPI server
        for name, service, extra in services:
            if name == "FastAPI Server" and hasattr(service, 'should_exit'):
                service.should_exit = True
        
        logger.info("All services stopped")


if __name__ == "__main__":
    logger.info("Starting HuskyApply Brain Service...")
    logger.info(f"Configuration: gRPC={os.getenv('GRPC_ENABLED', 'true')}, "
                f"RabbitMQ={os.getenv('RABBITMQ_ENABLED', 'true')}, "
                f"FastAPI={os.getenv('FASTAPI_HOST', '0.0.0.0')}:{os.getenv('FASTAPI_PORT', '8000')}")
    
    # Run the hybrid service
    asyncio.run(run_hybrid_service())
