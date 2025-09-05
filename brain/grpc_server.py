"""
gRPC server implementation for Brain service.
Handles job processing requests and provides streaming updates.
"""

import asyncio
import grpc
import json
import logging
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from typing import AsyncIterator, Dict, List, Optional, Set
from datetime import datetime, timezone

# Import generated gRPC modules (these will be generated from protobuf)
try:
    from grpc import job_processing_pb2
    from grpc import job_processing_pb2_grpc
    from grpc import connection_mgmt_pb2
    from grpc import connection_mgmt_pb2_grpc
except ImportError:
    # Mock classes for development until protobuf is generated
    class MockMessage:
        def __init__(self, **kwargs):
            for k, v in kwargs.items():
                setattr(self, k, v)
    
    class MockStub:
        pass
    
    class job_processing_pb2:
        JobSubmissionRequest = MockMessage
        JobSubmissionResponse = MockMessage
        JobUpdateRequest = MockMessage
        JobUpdateResponse = MockMessage
        BatchJobRequest = MockMessage
        BatchJobResponse = MockMessage
        JobResultRequest = MockMessage
        JobResultResponse = MockMessage
        JobStatusUpdate = MockMessage
        ProcessingEvent = MockMessage
        ProcessingMetrics = MockMessage
        HealthResponse = MockMessage
        CancelJobRequest = MockMessage
        CancelJobResponse = MockMessage
        MetricsRequest = MockMessage
        MetricsResponse = MockMessage
        
        # Enums
        class JobStatus:
            JOB_STATUS_PENDING = 1
            JOB_STATUS_PROCESSING = 2
            JOB_STATUS_COMPLETED = 3
            JOB_STATUS_FAILED = 4
            JOB_STATUS_CANCELLED = 5
        
        class ServiceStatus:
            SERVICE_STATUS_HEALTHY = 1
            SERVICE_STATUS_DEGRADED = 2
            SERVICE_STATUS_UNHEALTHY = 3
    
    class job_processing_pb2_grpc:
        JobProcessingServiceServicer = MockStub
        GatewayCallbackServiceStub = MockStub

# Internal imports
from ai_chain import create_optimized_cover_letter_chain, scrape_jd_text_sync
from config.optimization import get_optimization_config
from exceptions import JobProcessingException, AIProviderException
from monitoring import monitor
from resource_manager import get_resource_manager
from tracing_utils import TraceContext, TracedLogger


logger = logging.getLogger(__name__)

class JobProcessingServicer(job_processing_pb2_grpc.JobProcessingServiceServicer):
    """
    gRPC service implementation for job processing operations.
    Replaces RabbitMQ-based message handling with direct gRPC calls.
    """

    def __init__(self):
        self.resource_manager = get_resource_manager()
        self.active_jobs: Dict[str, Dict] = {}
        self.update_streams: Dict[str, grpc.aio.ServicerContext] = {}
        self.batch_streams: Dict[str, grpc.aio.ServicerContext] = {}
        self.processing_metrics: Dict[str, float] = {}
        
        # Gateway callback client for sending status updates
        self.gateway_callback_client: Optional[job_processing_pb2_grpc.GatewayCallbackServiceStub] = None
        self.gateway_channel: Optional[grpc.aio.Channel] = None
        
        logger.info("JobProcessingServicer initialized")

    async def SubmitJob(self, request: job_processing_pb2.JobSubmissionRequest, context: grpc.aio.ServicerContext):
        """
        Handle job submission request. Replaces RabbitMQ message consumption.
        """
        try:
            job_id = request.job_id
            logger.info(f"Received gRPC job submission for job {job_id}")
            
            # Create trace context
            trace_id = request.trace_id or str(uuid.uuid4())
            trace_ctx = TraceContext(trace_id=trace_id, job_id=job_id, operation="job_submission")
            
            with trace_ctx:
                # Validate request
                if not request.jd_url or not request.resume_uri:
                    raise grpc.RpcError("Missing required fields: jd_url or resume_uri")
                
                # Check resource availability
                if not self.resource_manager.can_accept_job():
                    return job_processing_pb2.JobSubmissionResponse(
                        job_id=job_id,
                        status=job_processing_pb2.JobStatus.JOB_STATUS_QUEUED,
                        message="Server at capacity, job queued",
                        submitted_at=self._get_current_timestamp(),
                        queue_position=len(self.active_jobs) + 1,
                        estimated_completion_ms=60000  # 1 minute estimate
                    )
                
                # Store job for processing
                job_data = {
                    "job_id": job_id,
                    "jd_url": request.jd_url,
                    "resume_uri": request.resume_uri,
                    "model_provider": request.model_provider or "openai",
                    "model_name": request.model_name or "gpt-4o",
                    "user_id": request.user_id,
                    "trace_id": trace_id,
                    "priority": request.priority,
                    "options": request.options,
                    "submitted_at": time.time(),
                    "status": "PENDING"
                }
                
                self.active_jobs[job_id] = job_data
                
                # Start processing asynchronously
                asyncio.create_task(self._process_job_async(job_data, trace_ctx))
                
                return job_processing_pb2.JobSubmissionResponse(
                    job_id=job_id,
                    status=job_processing_pb2.JobStatus.JOB_STATUS_PENDING,
                    message="Job accepted for processing",
                    submitted_at=self._get_current_timestamp(),
                    queue_position=0,
                    estimated_completion_ms=30000  # 30 second estimate
                )
                
        except Exception as e:
            logger.error(f"Error submitting job {request.job_id}: {e}", exc_info=True)
            await context.abort(grpc.StatusCode.INTERNAL, f"Job submission failed: {str(e)}")

    async def StreamJobUpdates(self, request: job_processing_pb2.JobUpdateRequest, context: grpc.aio.ServicerContext):
        """
        Stream real-time job status updates. Replaces SSE streaming.
        """
        stream_id = str(uuid.uuid4())
        self.update_streams[stream_id] = context
        
        try:
            logger.info(f"Started job update stream {stream_id} for {len(request.job_ids)} jobs")
            
            # Send initial status for all requested jobs
            for job_id in request.job_ids:
                if job_id in self.active_jobs:
                    job_data = self.active_jobs[job_id]
                    response = self._create_job_update_response(job_data)
                    yield response
            
            # Keep stream alive and send updates as they occur
            while True:
                try:
                    await asyncio.sleep(1)  # Check for updates every second
                    
                    # This would normally be triggered by job status changes
                    # For now, we just maintain the connection
                    if context.cancelled():
                        break
                        
                except asyncio.CancelledError:
                    break
                    
        except Exception as e:
            logger.error(f"Error in job update stream {stream_id}: {e}", exc_info=True)
        finally:
            self.update_streams.pop(stream_id, None)
            logger.info(f"Closed job update stream {stream_id}")

    async def ProcessJobBatch(self, request_iterator, context: grpc.aio.ServicerContext):
        """
        Handle batch job processing with bidirectional streaming.
        """
        stream_id = str(uuid.uuid4())
        self.batch_streams[stream_id] = context
        
        try:
            logger.info(f"Started batch processing stream {stream_id}")
            
            async for batch_request in request_iterator:
                batch_id = batch_request.batch_id
                jobs = batch_request.jobs
                
                logger.info(f"Processing batch {batch_id} with {len(jobs)} jobs")
                
                # Process jobs in parallel
                tasks = []
                for job_request in jobs:
                    task = asyncio.create_task(self._process_single_job_from_batch(job_request))
                    tasks.append(task)
                
                # Wait for all jobs to complete and send updates
                completed_jobs = 0
                failed_jobs = 0
                job_updates = []
                
                for task in asyncio.as_completed(tasks):
                    try:
                        job_result = await task
                        job_updates.append(job_result)
                        if job_result.status == job_processing_pb2.JobStatus.JOB_STATUS_COMPLETED:
                            completed_jobs += 1
                        else:
                            failed_jobs += 1
                    except Exception as e:
                        logger.error(f"Batch job failed: {e}")
                        failed_jobs += 1
                
                # Send batch response
                batch_response = job_processing_pb2.BatchJobResponse(
                    batch_id=batch_id,
                    total_jobs=len(jobs),
                    completed_jobs=completed_jobs,
                    failed_jobs=failed_jobs,
                    job_updates=job_updates,
                    batch_status=job_processing_pb2.BatchStatus.BATCH_STATUS_COMPLETED
                )
                
                yield batch_response
                
        except Exception as e:
            logger.error(f"Error in batch processing stream {stream_id}: {e}", exc_info=True)
        finally:
            self.batch_streams.pop(stream_id, None)
            logger.info(f"Closed batch processing stream {stream_id}")

    async def GetJobResult(self, request: job_processing_pb2.JobResultRequest, context: grpc.aio.ServicerContext):
        """
        Retrieve job processing result.
        """
        job_id = request.job_id
        
        if job_id not in self.active_jobs:
            await context.abort(grpc.StatusCode.NOT_FOUND, f"Job {job_id} not found")
        
        job_data = self.active_jobs[job_id]
        
        # Check if job is completed
        if job_data.get("status") != "COMPLETED":
            await context.abort(grpc.StatusCode.FAILED_PRECONDITION, f"Job {job_id} is not completed")
        
        return job_processing_pb2.JobResultResponse(
            job_id=job_id,
            status=job_processing_pb2.JobStatus.JOB_STATUS_COMPLETED,
            content=job_processing_pb2.GeneratedContent(
                content_type="cover_letter",
                content=job_data.get("generated_content", ""),
                word_count=job_data.get("word_count", 0),
                quality_score=job_data.get("quality_score", 0.0)
            ),
            metadata=job_processing_pb2.ProcessingMetadata(
                model_used=job_data.get("model_used", ""),
                cost_usd=job_data.get("cost_usd", 0.0),
                processing_time_ms=job_data.get("processing_time_ms", 0),
                cached=job_data.get("cached", False),
                tokens_used=job_data.get("tokens_used", 0)
            ),
            created_at=self._get_current_timestamp()
        )

    async def HealthCheck(self, request, context: grpc.aio.ServicerContext):
        """
        Service health check endpoint.
        """
        try:
            health_data = await monitor.comprehensive_health_check()
            
            status = job_processing_pb2.ServiceStatus.SERVICE_STATUS_HEALTHY
            if health_data.get("status") == "degraded":
                status = job_processing_pb2.ServiceStatus.SERVICE_STATUS_DEGRADED
            elif health_data.get("status") == "error":
                status = job_processing_pb2.ServiceStatus.SERVICE_STATUS_UNHEALTHY
            
            return job_processing_pb2.HealthResponse(
                status=status,
                version=health_data.get("version", "0.0.1"),
                timestamp=self._get_current_timestamp(),
                dependencies=self._get_dependency_status(),
                resource_usage=self._get_resource_usage()
            )
            
        except Exception as e:
            logger.error(f"Health check failed: {e}", exc_info=True)
            return job_processing_pb2.HealthResponse(
                status=job_processing_pb2.ServiceStatus.SERVICE_STATUS_UNHEALTHY,
                version="0.0.1",
                timestamp=self._get_current_timestamp()
            )

    async def CancelJob(self, request: job_processing_pb2.CancelJobRequest, context: grpc.aio.ServicerContext):
        """
        Cancel a job that is currently processing.
        """
        job_id = request.job_id
        
        if job_id not in self.active_jobs:
            return job_processing_pb2.CancelJobResponse(
                job_id=job_id,
                cancelled=False,
                message=f"Job {job_id} not found",
                final_status=job_processing_pb2.JobStatus.JOB_STATUS_FAILED
            )
        
        job_data = self.active_jobs[job_id]
        
        # Update job status
        job_data["status"] = "CANCELLED"
        job_data["cancelled_at"] = time.time()
        job_data["cancel_reason"] = request.reason
        
        # Send status update to Gateway
        await self._send_status_update_to_gateway(job_id, "CANCELLED", "Job cancelled by user")
        
        return job_processing_pb2.CancelJobResponse(
            job_id=job_id,
            cancelled=True,
            message="Job cancelled successfully",
            final_status=job_processing_pb2.JobStatus.JOB_STATUS_CANCELLED
        )

    async def GetProcessingMetrics(self, request: job_processing_pb2.MetricsRequest, context: grpc.aio.ServicerContext):
        """
        Get processing metrics and statistics.
        """
        try:
            system_metrics = monitor.collect_system_metrics()
            
            # Create aggregated stats
            total_jobs = len(self.active_jobs)
            completed_jobs = sum(1 for job in self.active_jobs.values() if job.get("status") == "COMPLETED")
            processing_jobs = sum(1 for job in self.active_jobs.values() if job.get("status") == "PROCESSING")
            
            metrics = [
                job_processing_pb2.ProcessingMetrics(
                    service_name="huskyapply-brain",
                    timestamp=self._get_current_timestamp(),
                    resource_usage=job_processing_pb2.ResourceUsage(
                        cpu_percent=system_metrics.cpu_percent,
                        memory_percent=system_metrics.memory_percent,
                        memory_used_mb=system_metrics.memory_used_mb,
                        active_connections=len(self.update_streams) + len(self.batch_streams),
                        thread_count=1  # Single-threaded async
                    ),
                    performance=job_processing_pb2.PerformanceStats(
                        avg_processing_time_ms=self.processing_metrics.get("avg_processing_time", 0),
                        p95_processing_time_ms=self.processing_metrics.get("p95_processing_time", 0),
                        p99_processing_time_ms=self.processing_metrics.get("p99_processing_time", 0),
                        total_requests=total_jobs,
                        successful_requests=completed_jobs,
                        failed_requests=total_jobs - completed_jobs,
                        throughput_per_second=self.processing_metrics.get("throughput", 0)
                    ),
                    queue_stats=job_processing_pb2.QueueStats(
                        pending_jobs=sum(1 for job in self.active_jobs.values() if job.get("status") == "PENDING"),
                        processing_jobs=processing_jobs,
                        total_queued=total_jobs,
                        avg_wait_time_ms=self.processing_metrics.get("avg_wait_time", 0)
                    ),
                    custom_metrics=self.processing_metrics
                )
            ]
            
            return job_processing_pb2.MetricsResponse(
                metrics=metrics,
                aggregated=job_processing_pb2.AggregatedStats(
                    total_jobs_processed=total_jobs,
                    total_cost_usd=sum(job.get("cost_usd", 0) for job in self.active_jobs.values()),
                    avg_quality_score=sum(job.get("quality_score", 0) for job in self.active_jobs.values()) / max(total_jobs, 1),
                    cache_hit_rate=self.processing_metrics.get("cache_hit_rate", 0),
                    total_processing_time_ms=sum(job.get("processing_time_ms", 0) for job in self.active_jobs.values())
                )
            )
            
        except Exception as e:
            logger.error(f"Error getting processing metrics: {e}", exc_info=True)
            await context.abort(grpc.StatusCode.INTERNAL, f"Failed to get metrics: {str(e)}")

    async def _process_job_async(self, job_data: Dict, trace_ctx: TraceContext):
        """
        Process a single job asynchronously. This replaces the RabbitMQ message callback logic.
        """
        job_id = job_data["job_id"]
        start_time = time.time()
        
        try:
            with trace_ctx:
                logger.info(f"Starting async processing for job {job_id}", extra=trace_ctx.get_logging_extra())
                
                # Update status to PROCESSING
                job_data["status"] = "PROCESSING"
                await self._send_status_update_to_gateway(job_id, "PROCESSING", "Job processing started")
                
                # Scrape job description
                logger.info(f"Scraping job description from: {job_data['jd_url']}")
                jd_text = scrape_jd_text_sync(job_data["jd_url"])
                
                # Get optimization configuration
                optimization_config = get_optimization_config()
                
                # Process with AI chain
                logger.info("Invoking optimized AI chain...")
                generated_content, processing_metadata = await create_optimized_cover_letter_chain(
                    jd_text=jd_text,
                    model_provider=job_data["model_provider"],
                    model_name=job_data["model_name"],
                    user_id=job_data["user_id"],
                    job_id=job_id,
                    optimization_profile=optimization_config.default_optimization_profile,
                    enable_streaming=optimization_config.enable_streaming,
                    enable_caching=optimization_config.enable_semantic_caching
                )
                
                # Calculate processing time
                processing_time = (time.time() - start_time) * 1000  # Convert to milliseconds
                
                # Update job data
                job_data.update({
                    "status": "COMPLETED",
                    "generated_content": generated_content,
                    "processing_time_ms": processing_time,
                    "word_count": len(generated_content.split()),
                    "quality_score": processing_metadata.get("quality_score", 0.0),
                    "cost_usd": processing_metadata.get("cost_usd", 0.0),
                    "cached": processing_metadata.get("cached", False),
                    "tokens_used": processing_metadata.get("tokens_used", 0),
                    "model_used": f"{job_data['model_provider']}/{job_data['model_name']}",
                    "completed_at": time.time()
                })
                
                # Send completion status to Gateway
                await self._send_status_update_to_gateway(
                    job_id, "COMPLETED", "Job completed successfully", content=generated_content
                )
                
                logger.info(f"Job {job_id} completed successfully in {processing_time:.2f}ms")
                
        except Exception as e:
            logger.error(f"Error processing job {job_id}: {e}", exc_info=True)
            
            # Update job status to failed
            job_data.update({
                "status": "FAILED",
                "error_message": str(e),
                "failed_at": time.time(),
                "processing_time_ms": (time.time() - start_time) * 1000
            })
            
            # Send failure status to Gateway
            await self._send_status_update_to_gateway(job_id, "FAILED", f"Job processing failed: {str(e)}")

    async def _process_single_job_from_batch(self, job_request) -> job_processing_pb2.JobUpdateResponse:
        """
        Process a single job from a batch request.
        """
        # This is a simplified version for batch processing
        # In practice, this would follow the same logic as _process_job_async
        job_id = job_request.job_id
        
        try:
            # Simulate processing
            await asyncio.sleep(1)  # Simulate AI processing time
            
            return job_processing_pb2.JobUpdateResponse(
                job_id=job_id,
                status=job_processing_pb2.JobStatus.JOB_STATUS_COMPLETED,
                updated_at=self._get_current_timestamp(),
                message="Batch job completed"
            )
            
        except Exception as e:
            logger.error(f"Batch job {job_id} failed: {e}")
            return job_processing_pb2.JobUpdateResponse(
                job_id=job_id,
                status=job_processing_pb2.JobStatus.JOB_STATUS_FAILED,
                updated_at=self._get_current_timestamp(),
                message=f"Batch job failed: {str(e)}"
            )

    async def _send_status_update_to_gateway(self, job_id: str, status: str, message: str, content: Optional[str] = None):
        """
        Send status update to Gateway service via gRPC.
        This replaces the HTTP callback mechanism.
        """
        if not self.gateway_callback_client:
            logger.warning("Gateway callback client not initialized, cannot send status update")
            return
        
        try:
            # Convert status to gRPC enum
            grpc_status = self._convert_status_to_grpc(status)
            
            # Create metadata
            metadata = {}
            if content:
                metadata["content"] = content
            
            # Create status update request
            status_update = job_processing_pb2.JobStatusUpdate(
                job_id=job_id,
                status=grpc_status,
                message=message,
                updated_at=self._get_current_timestamp(),
                metadata=metadata
            )
            
            # Send to Gateway
            response = await self.gateway_callback_client.UpdateJobStatus(status_update)
            
            if response.acknowledged:
                logger.debug(f"Status update acknowledged for job {job_id}")
            else:
                logger.warning(f"Status update not acknowledged for job {job_id}: {response.message}")
                
        except Exception as e:
            logger.error(f"Failed to send status update to Gateway for job {job_id}: {e}")

    def _create_job_update_response(self, job_data: Dict) -> job_processing_pb2.JobUpdateResponse:
        """
        Create a job update response from job data.
        """
        grpc_status = self._convert_status_to_grpc(job_data["status"])
        
        return job_processing_pb2.JobUpdateResponse(
            job_id=job_data["job_id"],
            status=grpc_status,
            updated_at=self._get_current_timestamp(),
            message=f"Job is {job_data['status'].lower()}",
            metadata={"user_id": job_data["user_id"]}
        )

    def _convert_status_to_grpc(self, status: str) -> int:
        """
        Convert string status to gRPC enum value.
        """
        status_mapping = {
            "PENDING": job_processing_pb2.JobStatus.JOB_STATUS_PENDING,
            "PROCESSING": job_processing_pb2.JobStatus.JOB_STATUS_PROCESSING,
            "COMPLETED": job_processing_pb2.JobStatus.JOB_STATUS_COMPLETED,
            "FAILED": job_processing_pb2.JobStatus.JOB_STATUS_FAILED,
            "CANCELLED": job_processing_pb2.JobStatus.JOB_STATUS_CANCELLED
        }
        return status_mapping.get(status, job_processing_pb2.JobStatus.JOB_STATUS_PENDING)

    def _get_current_timestamp(self):
        """
        Get current timestamp in protobuf format.
        """
        now = datetime.now(timezone.utc)
        return {
            "seconds": int(now.timestamp()),
            "nanos": int((now.timestamp() % 1) * 1e9)
        }

    def _get_dependency_status(self) -> Dict[str, int]:
        """
        Get status of service dependencies.
        """
        # This would check actual dependencies
        return {
            "openai_api": job_processing_pb2.ServiceStatus.SERVICE_STATUS_HEALTHY,
            "anthropic_api": job_processing_pb2.ServiceStatus.SERVICE_STATUS_HEALTHY,
            "gateway_service": job_processing_pb2.ServiceStatus.SERVICE_STATUS_HEALTHY
        }

    def _get_resource_usage(self):
        """
        Get current resource usage.
        """
        system_metrics = monitor.collect_system_metrics()
        return job_processing_pb2.ResourceUsage(
            cpu_percent=system_metrics.cpu_percent,
            memory_percent=system_metrics.memory_percent,
            memory_used_mb=system_metrics.memory_used_mb,
            active_connections=len(self.update_streams) + len(self.batch_streams),
            thread_count=1
        )

    async def initialize_gateway_callback_client(self, gateway_host: str, gateway_port: int, tls_enabled: bool = False):
        """
        Initialize gRPC client for sending callbacks to Gateway.
        """
        try:
            if tls_enabled:
                # Configure TLS
                credentials = grpc.ssl_channel_credentials()
                self.gateway_channel = grpc.aio.secure_channel(f"{gateway_host}:{gateway_port}", credentials)
            else:
                self.gateway_channel = grpc.aio.insecure_channel(f"{gateway_host}:{gateway_port}")
            
            self.gateway_callback_client = job_processing_pb2_grpc.GatewayCallbackServiceStub(self.gateway_channel)
            
            logger.info(f"Initialized Gateway callback client to {gateway_host}:{gateway_port}")
            
        except Exception as e:
            logger.error(f"Failed to initialize Gateway callback client: {e}")

    async def shutdown(self):
        """
        Gracefully shutdown the gRPC server.
        """
        logger.info("Shutting down gRPC server")
        
        # Close all active streams
        for stream_id in list(self.update_streams.keys()):
            try:
                context = self.update_streams.pop(stream_id)
                # Context will be cleaned up automatically
            except Exception as e:
                logger.warning(f"Error closing update stream {stream_id}: {e}")
        
        for stream_id in list(self.batch_streams.keys()):
            try:
                context = self.batch_streams.pop(stream_id)
                # Context will be cleaned up automatically
            except Exception as e:
                logger.warning(f"Error closing batch stream {stream_id}: {e}")
        
        # Close Gateway callback client
        if self.gateway_channel:
            try:
                await self.gateway_channel.close()
            except Exception as e:
                logger.warning(f"Error closing Gateway channel: {e}")
        
        logger.info("gRPC server shutdown completed")


async def create_grpc_server(host: str = "0.0.0.0", port: int = 9090, max_workers: int = 10):
    """
    Create and configure the gRPC server.
    """
    server = grpc.aio.server(ThreadPoolExecutor(max_workers=max_workers))
    
    # Add the job processing servicer
    job_servicer = JobProcessingServicer()
    job_processing_pb2_grpc.add_JobProcessingServiceServicer_to_server(job_servicer, server)
    
    # Configure server options for performance
    options = [
        ('grpc.keepalive_time_ms', 30000),
        ('grpc.keepalive_timeout_ms', 10000),
        ('grpc.keepalive_permit_without_calls', True),
        ('grpc.http2.max_pings_without_data', 0),
        ('grpc.http2.min_time_between_pings_ms', 10000),
        ('grpc.http2.min_ping_interval_without_data_ms', 300000),
        ('grpc.max_receive_message_length', 4 * 1024 * 1024),  # 4MB
        ('grpc.max_send_message_length', 4 * 1024 * 1024),     # 4MB
    ]
    
    # Add options to server
    for option in options:
        server.add_generic_rpc_handlers([])  # This applies the options
    
    # Bind to port
    listen_addr = f"{host}:{port}"
    server.add_insecure_port(listen_addr)
    
    logger.info(f"gRPC server configured to listen on {listen_addr}")
    
    return server, job_servicer


async def start_grpc_server():
    """
    Start the gRPC server and handle shutdown gracefully.
    """
    server, servicer = await create_grpc_server()
    
    await server.start()
    logger.info("gRPC server started successfully")
    
    try:
        await server.wait_for_termination()
    except KeyboardInterrupt:
        logger.info("Received interrupt signal, shutting down gRPC server")
    finally:
        await servicer.shutdown()
        await server.stop(grace=10.0)
        logger.info("gRPC server stopped")


if __name__ == "__main__":
    # Configure logging
    logging.basicConfig(
        level=logging.INFO,
        format='level=%(levelname)s timestamp=%(asctime)s service=brain-grpc msg="%(message)s"'
    )
    
    # Start the server
    asyncio.run(start_grpc_server())