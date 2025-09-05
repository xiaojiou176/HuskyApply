"""
Resource management utilities for the HuskyApply Brain service.

This module provides comprehensive resource cleanup mechanisms including:
- HTTP client connection pooling and management
- Memory management for AI processing pipelines
- RabbitMQ connection pooling
- Graceful shutdown procedures
- Resource monitoring and alerting
"""

import asyncio
import gc
import logging
import os
import signal
import threading
import time
import weakref
from contextlib import asynccontextmanager, contextmanager
from dataclasses import dataclass, field
from typing import Any, AsyncIterator, Dict, Iterator, List, Optional, Set, Type, Union
from weakref import WeakSet

import httpx
import pika
import psutil
from prometheus_client import Counter, Gauge, Histogram

from exceptions import BrainServiceException

logger = logging.getLogger(__name__)


@dataclass
class ResourceMetrics:
    """Resource usage metrics for monitoring."""
    
    memory_used_mb: float
    memory_percent: float
    cpu_percent: float
    active_connections: int
    open_files: int
    thread_count: int
    gc_objects: int
    timestamp: float = field(default_factory=time.time)


@dataclass
class ConnectionPoolStats:
    """Connection pool statistics."""
    
    pool_size: int
    active_connections: int
    idle_connections: int
    created_total: int
    closed_total: int
    errors_total: int


class HTTPClientPool:
    """
    HTTP client pool manager with automatic cleanup and resource limits.
    
    Manages a pool of reusable HTTP clients to prevent resource leaks
    and improve performance through connection reuse.
    """
    
    def __init__(
        self,
        max_pool_size: int = 10,
        max_connections_per_host: int = 5,
        timeout: float = 30.0,
        max_keepalive_connections: int = 20,
        keepalive_expiry: float = 5.0,
    ):
        self.max_pool_size = max_pool_size
        self.timeout = timeout
        self._pool: List[httpx.AsyncClient] = []
        self._pool_lock = asyncio.Lock()
        self._created_count = 0
        self._closed_count = 0
        self._error_count = 0
        
        # HTTP client configuration
        self._limits = httpx.Limits(
            max_connections=max_connections_per_host,
            max_keepalive_connections=max_keepalive_connections,
            keepalive_expiry=keepalive_expiry,
        )
        
        # Prometheus metrics
        self._pool_size_gauge = Gauge(
            "http_client_pool_size", "Current HTTP client pool size"
        )
        self._active_connections_gauge = Gauge(
            "http_client_active_connections", "Active HTTP connections"
        )
        self._pool_requests_counter = Counter(
            "http_client_pool_requests_total", "Total HTTP client pool requests", ["status"]
        )
    
    async def get_client(self) -> httpx.AsyncClient:
        """Get an HTTP client from the pool or create a new one."""
        async with self._pool_lock:
            if self._pool:
                client = self._pool.pop()
                if not client.is_closed:
                    self._pool_requests_counter.labels(status="reused").inc()
                    return client
                else:
                    # Client was closed, create a new one
                    await client.aclose()
            
            # Create new client
            client = httpx.AsyncClient(
                timeout=httpx.Timeout(self.timeout),
                limits=self._limits,
                headers={"User-Agent": "HuskyApply-Brain/1.0"},
            )
            self._created_count += 1
            self._pool_requests_counter.labels(status="created").inc()
            
            logger.debug(f"Created new HTTP client. Pool size: {len(self._pool)}")
            return client
    
    async def return_client(self, client: httpx.AsyncClient) -> None:
        """Return an HTTP client to the pool."""
        if client.is_closed:
            return
            
        async with self._pool_lock:
            if len(self._pool) < self.max_pool_size:
                self._pool.append(client)
                self._pool_size_gauge.set(len(self._pool))
                logger.debug(f"Returned client to pool. Pool size: {len(self._pool)}")
            else:
                # Pool is full, close the client
                await client.aclose()
                self._closed_count += 1
    
    @asynccontextmanager
    async def client(self) -> AsyncIterator[httpx.AsyncClient]:
        """Context manager for HTTP client usage."""
        client = await self.get_client()
        try:
            yield client
        except Exception as e:
            self._error_count += 1
            self._pool_requests_counter.labels(status="error").inc()
            logger.error(f"HTTP client error: {e}")
            raise
        finally:
            await self.return_client(client)
    
    async def close_all(self) -> None:
        """Close all pooled HTTP clients."""
        async with self._pool_lock:
            for client in self._pool:
                await client.aclose()
            self._pool.clear()
            self._pool_size_gauge.set(0)
            logger.info(f"Closed all HTTP clients. Total created: {self._created_count}")
    
    def get_stats(self) -> ConnectionPoolStats:
        """Get connection pool statistics."""
        return ConnectionPoolStats(
            pool_size=len(self._pool),
            active_connections=self._created_count - len(self._pool) - self._closed_count,
            idle_connections=len(self._pool),
            created_total=self._created_count,
            closed_total=self._closed_count,
            errors_total=self._error_count,
        )


class RabbitMQConnectionPool:
    """
    RabbitMQ connection pool with automatic reconnection and cleanup.
    """
    
    def __init__(
        self,
        max_connections: int = 3,
        connection_params: Optional[pika.ConnectionParameters] = None,
    ):
        self.max_connections = max_connections
        self.connection_params = connection_params
        self._connections: List[pika.BlockingConnection] = []
        self._lock = threading.Lock()
        self._created_count = 0
        self._error_count = 0
    
    def get_connection(self) -> pika.BlockingConnection:
        """Get a connection from the pool or create a new one."""
        with self._lock:
            # Try to find a healthy connection
            for connection in self._connections[:]:
                if connection.is_open:
                    self._connections.remove(connection)
                    return connection
                else:
                    # Remove closed connection
                    self._connections.remove(connection)
            
            # Create new connection
            if self.connection_params:
                connection = pika.BlockingConnection(self.connection_params)
                self._created_count += 1
                logger.debug(f"Created new RabbitMQ connection. Total: {self._created_count}")
                return connection
            else:
                raise BrainServiceException("No RabbitMQ connection parameters configured")
    
    def return_connection(self, connection: pika.BlockingConnection) -> None:
        """Return a connection to the pool."""
        if not connection.is_open:
            return
            
        with self._lock:
            if len(self._connections) < self.max_connections:
                self._connections.append(connection)
            else:
                # Pool is full, close the connection
                connection.close()
    
    @contextmanager
    def connection(self) -> Iterator[pika.BlockingConnection]:
        """Context manager for RabbitMQ connection usage."""
        connection = self.get_connection()
        try:
            yield connection
        except Exception as e:
            self._error_count += 1
            logger.error(f"RabbitMQ connection error: {e}")
            raise
        finally:
            self.return_connection(connection)
    
    def close_all(self) -> None:
        """Close all pooled connections."""
        with self._lock:
            for connection in self._connections:
                if connection.is_open:
                    connection.close()
            self._connections.clear()
            logger.info(f"Closed all RabbitMQ connections. Total created: {self._created_count}")


class MemoryManager:
    """
    Memory management utility for AI processing pipelines.
    
    Tracks and manages memory usage of AI models, LangChain objects,
    and other memory-intensive operations.
    """
    
    def __init__(
        self,
        memory_threshold_mb: float = 512.0,
        gc_collection_interval: float = 60.0,
        cleanup_threshold: float = 0.8,  # Trigger cleanup at 80% memory usage
    ):
        self.memory_threshold_mb = memory_threshold_mb
        self.gc_collection_interval = gc_collection_interval
        self.cleanup_threshold = cleanup_threshold
        
        # Track objects for cleanup
        self._tracked_objects: WeakSet[Any] = WeakSet()
        self._last_gc_time = time.time()
        
        # Metrics
        self._memory_cleanups_counter = Counter(
            "memory_cleanups_total", "Total memory cleanup operations", ["type"]
        )
        self._memory_usage_gauge = Gauge(
            "process_memory_usage_mb", "Current process memory usage in MB"
        )
        self._tracked_objects_gauge = Gauge(
            "tracked_objects_count", "Number of tracked objects for cleanup"
        )
    
    def register_object(self, obj: Any, cleanup_func: Optional[callable] = None) -> None:
        """Register an object for memory tracking and cleanup."""
        self._tracked_objects.add(obj)
        if cleanup_func:
            # Store cleanup function as a weak reference
            weakref.finalize(obj, cleanup_func)
        
        self._tracked_objects_gauge.set(len(self._tracked_objects))
        logger.debug(f"Registered object for memory tracking: {type(obj).__name__}")
    
    def force_cleanup(self, cleanup_type: str = "manual") -> None:
        """Force garbage collection and memory cleanup."""
        start_time = time.time()
        
        # Clear weak references to dead objects
        self._tracked_objects = WeakSet(obj for obj in self._tracked_objects if obj is not None)
        
        # Force garbage collection
        collected = gc.collect()
        
        # Update metrics
        self._memory_cleanups_counter.labels(type=cleanup_type).inc()
        self._tracked_objects_gauge.set(len(self._tracked_objects))
        
        cleanup_time = (time.time() - start_time) * 1000
        logger.info(
            f"Memory cleanup completed. Collected {collected} objects in {cleanup_time:.1f}ms. "
            f"Tracking {len(self._tracked_objects)} objects."
        )
    
    def check_memory_usage(self) -> ResourceMetrics:
        """Check current memory usage and trigger cleanup if needed."""
        process = psutil.Process()
        memory_info = process.memory_info()
        memory_mb = memory_info.rss / (1024 * 1024)
        memory_percent = process.memory_percent()
        
        # Update metrics
        self._memory_usage_gauge.set(memory_mb)
        
        # Check if cleanup is needed
        current_time = time.time()
        if (
            memory_mb > self.memory_threshold_mb * self.cleanup_threshold
            or current_time - self._last_gc_time > self.gc_collection_interval
        ):
            self.force_cleanup("automatic")
            self._last_gc_time = current_time
        
        return ResourceMetrics(
            memory_used_mb=memory_mb,
            memory_percent=memory_percent,
            cpu_percent=process.cpu_percent(),
            active_connections=len([conn for conn in process.connections() if conn.status == "ESTABLISHED"]),
            open_files=process.num_fds() if hasattr(process, "num_fds") else 0,
            thread_count=process.num_threads(),
            gc_objects=len(gc.get_objects()),
        )
    
    def cleanup_ai_objects(self) -> None:
        """Specific cleanup for AI/LangChain objects."""
        # This would be called after AI processing to clean up model instances
        self.force_cleanup("ai_processing")


class GracefulShutdownHandler:
    """
    Handles graceful shutdown of all resources and services.
    """
    
    def __init__(self, timeout: float = 30.0):
        self.timeout = timeout
        self.shutdown_event = threading.Event()
        self.cleanup_functions: List[callable] = []
        self._shutdown_in_progress = False
        
        # Register signal handlers
        signal.signal(signal.SIGINT, self._signal_handler)
        signal.signal(signal.SIGTERM, self._signal_handler)
    
    def register_cleanup_function(self, func: callable) -> None:
        """Register a cleanup function to be called during shutdown."""
        self.cleanup_functions.append(func)
        logger.debug(f"Registered cleanup function: {func.__name__}")
    
    def _signal_handler(self, signum: int, frame: Any) -> None:
        """Handle shutdown signals."""
        signal_name = signal.Signals(signum).name
        logger.info(f"Received {signal_name} signal, initiating graceful shutdown...")
        self.initiate_shutdown()
    
    def initiate_shutdown(self) -> None:
        """Initiate graceful shutdown process."""
        if self._shutdown_in_progress:
            logger.warning("Shutdown already in progress")
            return
            
        self._shutdown_in_progress = True
        self.shutdown_event.set()
        
        # Execute cleanup functions
        for cleanup_func in reversed(self.cleanup_functions):  # LIFO order
            try:
                logger.info(f"Executing cleanup function: {cleanup_func.__name__}")
                cleanup_func()
            except Exception as e:
                logger.error(f"Error during cleanup in {cleanup_func.__name__}: {e}")
        
        logger.info("Graceful shutdown completed")
    
    def wait_for_shutdown(self, timeout: Optional[float] = None) -> bool:
        """Wait for shutdown signal."""
        return self.shutdown_event.wait(timeout or self.timeout)


class ResourceManager:
    """
    Central resource manager for the Brain service.
    
    Coordinates all resource management activities including HTTP clients,
    RabbitMQ connections, memory management, and graceful shutdown.
    """
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        from config.resource_config import get_resource_config
        
        # Load configuration
        self.resource_config = get_resource_config()
        self.config = config or {}
        
        # Initialize resource managers
        self.http_pool = HTTPClientPool(
            max_pool_size=self.resource_config.http_pool.max_pool_size,
            max_connections_per_host=self.resource_config.http_pool.max_connections_per_host,
            timeout=self.resource_config.http_pool.timeout,
            max_keepalive_connections=self.resource_config.http_pool.max_keepalive_connections,
            keepalive_expiry=self.resource_config.http_pool.keepalive_expiry,
        )
        
        self.rabbitmq_pool = RabbitMQConnectionPool(
            max_connections=self.resource_config.rabbitmq_pool.max_connections
        )
        
        self.memory_manager = MemoryManager(
            memory_threshold_mb=self.resource_config.memory.threshold_mb,
            gc_collection_interval=self.resource_config.memory.gc_interval,
            cleanup_threshold=self.resource_config.memory.cleanup_threshold,
        )
        
        self.shutdown_handler = GracefulShutdownHandler(
            timeout=self.resource_config.shutdown.timeout
        )
        
        # Register cleanup functions
        self.shutdown_handler.register_cleanup_function(self._cleanup_http_pool)
        self.shutdown_handler.register_cleanup_function(self._cleanup_rabbitmq_pool)
        self.shutdown_handler.register_cleanup_function(self._cleanup_memory)
        
        # Start monitoring
        self._monitoring_task: Optional[asyncio.Task] = None
        self._start_monitoring()
    
    def _start_monitoring(self) -> None:
        """Start resource monitoring task."""
        async def monitor():
            while not self.shutdown_handler.shutdown_event.is_set():
                try:
                    # Check resource usage
                    metrics = self.memory_manager.check_memory_usage()
                    
                    # Log resource statistics periodically
                    if int(time.time()) % int(self.resource_config.monitoring.stats_log_interval) == 0:
                        self._log_resource_stats(metrics)
                    
                    # Use configured monitoring interval
                    await asyncio.sleep(self.resource_config.memory.memory_monitor_interval)
                except Exception as e:
                    logger.error(f"Resource monitoring error: {e}")
                    await asyncio.sleep(60)  # Back off on error
        
        # Schedule monitoring task
        try:
            loop = asyncio.get_event_loop()
            self._monitoring_task = loop.create_task(monitor())
        except RuntimeError:
            logger.info("No event loop running, monitoring will start when loop is available")
    
    def _log_resource_stats(self, metrics: ResourceMetrics) -> None:
        """Log resource statistics and evaluate alerts."""
        http_stats = self.http_pool.get_stats()
        
        logger.info(
            f"Resource Stats - Memory: {metrics.memory_used_mb:.1f}MB ({metrics.memory_percent:.1f}%), "
            f"CPU: {metrics.cpu_percent:.1f}%, Connections: {metrics.active_connections}, "
            f"Threads: {metrics.thread_count}, HTTP Pool: {http_stats.pool_size}/{http_stats.created_total}"
        )
        
        # Evaluate resource alerts
        try:
            from resource_alerts import evaluate_resource_alerts
            
            additional_metrics = {
                "http_pool_utilization": (http_stats.active_connections / max(1, http_stats.pool_size)) * 100,
                "tracked_ai_objects": len(self.memory_manager._tracked_objects),
                "http_pool_errors": http_stats.errors_total,
            }
            
            alerts = evaluate_resource_alerts(metrics, additional_metrics)
            if alerts:
                logger.info(f"Generated {len(alerts)} resource alerts")
        except Exception as e:
            logger.error(f"Error evaluating resource alerts: {e}")
    
    async def _cleanup_http_pool(self) -> None:
        """Cleanup HTTP client pool."""
        await self.http_pool.close_all()
    
    def _cleanup_rabbitmq_pool(self) -> None:
        """Cleanup RabbitMQ connection pool."""
        self.rabbitmq_pool.close_all()
    
    def _cleanup_memory(self) -> None:
        """Cleanup memory resources."""
        self.memory_manager.force_cleanup("shutdown")
    
    async def get_http_client(self) -> httpx.AsyncClient:
        """Get an HTTP client from the pool."""
        return await self.http_pool.get_client()
    
    async def return_http_client(self, client: httpx.AsyncClient) -> None:
        """Return an HTTP client to the pool."""
        await self.http_pool.return_client(client)
    
    @asynccontextmanager
    async def http_client(self) -> AsyncIterator[httpx.AsyncClient]:
        """Context manager for HTTP client usage."""
        async with self.http_pool.client() as client:
            yield client
    
    def get_rabbitmq_connection(self) -> pika.BlockingConnection:
        """Get a RabbitMQ connection from the pool."""
        return self.rabbitmq_pool.get_connection()
    
    @contextmanager
    def rabbitmq_connection(self) -> Iterator[pika.BlockingConnection]:
        """Context manager for RabbitMQ connection usage."""
        with self.rabbitmq_pool.connection() as connection:
            yield connection
    
    def register_ai_object(self, obj: Any, cleanup_func: Optional[callable] = None) -> None:
        """Register an AI object for memory tracking."""
        self.memory_manager.register_object(obj, cleanup_func)
    
    def cleanup_ai_resources(self) -> None:
        """Cleanup AI processing resources."""
        self.memory_manager.cleanup_ai_objects()
    
    def get_resource_metrics(self) -> ResourceMetrics:
        """Get current resource metrics."""
        return self.memory_manager.check_memory_usage()
    
    async def shutdown(self) -> None:
        """Initiate graceful shutdown."""
        self.shutdown_handler.initiate_shutdown()
        
        # Cancel monitoring task
        if self._monitoring_task and not self._monitoring_task.done():
            self._monitoring_task.cancel()
            try:
                await self._monitoring_task
            except asyncio.CancelledError:
                pass


# Global resource manager instance
_resource_manager: Optional[ResourceManager] = None


def get_resource_manager(config: Optional[Dict[str, Any]] = None) -> ResourceManager:
    """Get the global resource manager instance."""
    global _resource_manager
    if _resource_manager is None:
        # Load configuration from environment
        default_config = {
            "http_pool_size": int(os.getenv("HTTP_POOL_SIZE", "10")),
            "http_timeout": float(os.getenv("HTTP_TIMEOUT", "30.0")),
            "rabbitmq_pool_size": int(os.getenv("RABBITMQ_POOL_SIZE", "3")),
            "memory_threshold_mb": float(os.getenv("MEMORY_THRESHOLD_MB", "512.0")),
            "gc_interval": float(os.getenv("GC_INTERVAL", "60.0")),
            "shutdown_timeout": float(os.getenv("SHUTDOWN_TIMEOUT", "30.0")),
        }
        default_config.update(config or {})
        _resource_manager = ResourceManager(default_config)
    return _resource_manager


# Convenience functions for global access
async def get_http_client() -> httpx.AsyncClient:
    """Get an HTTP client from the global resource manager."""
    return await get_resource_manager().get_http_client()


async def return_http_client(client: httpx.AsyncClient) -> None:
    """Return an HTTP client to the global resource manager."""
    await get_resource_manager().return_http_client(client)


@asynccontextmanager
async def http_client() -> AsyncIterator[httpx.AsyncClient]:
    """Context manager for HTTP client usage."""
    async with get_resource_manager().http_client() as client:
        yield client


@contextmanager
def rabbitmq_connection() -> Iterator[pika.BlockingConnection]:
    """Context manager for RabbitMQ connection usage."""
    with get_resource_manager().rabbitmq_connection() as connection:
        yield connection


def cleanup_ai_resources() -> None:
    """Cleanup AI processing resources."""
    get_resource_manager().cleanup_ai_resources()


def register_ai_object(obj: Any, cleanup_func: Optional[callable] = None) -> None:
    """Register an AI object for memory tracking."""
    get_resource_manager().register_ai_object(obj, cleanup_func)