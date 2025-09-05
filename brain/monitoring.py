"""
Performance monitoring and metrics collection for the Brain service.

This module provides comprehensive monitoring capabilities including
system metrics, custom business metrics, and health check utilities.
"""

import asyncio
import os
import time
from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Any, Dict, List, Optional

import httpx
import pika
import psutil
from prometheus_client import CollectorRegistry, Counter, Gauge, Histogram

from exceptions import BrainServiceException


@dataclass
class SystemMetrics:
    """System performance metrics."""

    cpu_percent: float
    memory_percent: float
    memory_used_mb: float
    memory_available_mb: float
    disk_usage_percent: float
    uptime_seconds: float


@dataclass
class HealthCheckResult:
    """Result of a health check operation."""

    component: str
    status: str  # 'ok', 'warning', 'error'
    message: str
    response_time_ms: Optional[float] = None
    details: Optional[Dict[str, Any]] = None


class PerformanceMonitor:
    """Performance monitoring and metrics collection."""

    def __init__(self, registry: Optional[CollectorRegistry] = None):
        """Initialize performance monitor with optional custom registry."""
        self.start_time = time.time()
        self.registry = registry

        # System metrics
        self.cpu_gauge = Gauge(
            "system_cpu_usage_percent", "Current CPU usage percentage", registry=registry
        )
        self.memory_gauge = Gauge(
            "system_memory_usage_percent", "Current memory usage percentage", registry=registry
        )
        self.disk_gauge = Gauge(
            "system_disk_usage_percent", "Current disk usage percentage", registry=registry
        )
        self.uptime_gauge = Gauge(
            "service_uptime_seconds", "Service uptime in seconds", registry=registry
        )

        # Business metrics
        self.health_check_counter = Counter(
            "health_checks_total",
            "Total number of health checks performed",
            ["component", "status"],
            registry=registry,
        )
        self.health_check_duration = Histogram(
            "health_check_duration_seconds",
            "Time spent performing health checks",
            ["component"],
            registry=registry,
        )

        # Component availability
        self.component_status = Gauge(
            "component_availability",
            "Component availability (1=ok, 0.5=degraded, 0=error)",
            ["component"],
            registry=registry,
        )

    def collect_system_metrics(self) -> SystemMetrics:
        """Collect current system performance metrics."""
        # CPU usage
        cpu_percent = psutil.cpu_percent(interval=0.1)

        # Memory usage
        memory = psutil.virtual_memory()
        memory_percent = memory.percent
        memory_used_mb = memory.used / (1024 * 1024)
        memory_available_mb = memory.available / (1024 * 1024)

        # Disk usage (root partition)
        disk = psutil.disk_usage("/")
        disk_usage_percent = (disk.used / disk.total) * 100

        # Service uptime
        uptime_seconds = time.time() - self.start_time

        # Update Prometheus metrics
        self.cpu_gauge.set(cpu_percent)
        self.memory_gauge.set(memory_percent)
        self.disk_gauge.set(disk_usage_percent)
        self.uptime_gauge.set(uptime_seconds)

        return SystemMetrics(
            cpu_percent=cpu_percent,
            memory_percent=memory_percent,
            memory_used_mb=memory_used_mb,
            memory_available_mb=memory_available_mb,
            disk_usage_percent=disk_usage_percent,
            uptime_seconds=uptime_seconds,
        )

    async def check_rabbitmq_health(
        self,
        host: str = "localhost",
        port: int = 5672,
        username: str = "husky",
        password: str = "husky",
        timeout: float = 5.0,
    ) -> HealthCheckResult:
        """Check RabbitMQ connection health."""
        start_time = time.time()

        with self.health_check_duration.labels(component="rabbitmq").time():
            try:
                credentials = pika.PlainCredentials(username, password)
                connection_params = pika.ConnectionParameters(
                    host=host,
                    port=port,
                    credentials=credentials,
                    connection_attempts=1,
                    retry_delay=1,
                    socket_timeout=timeout,
                )

                connection = pika.BlockingConnection(connection_params)

                # Test queue operations
                channel = connection.channel()
                test_queue = "health_check_queue"
                channel.queue_declare(queue=test_queue, durable=False, auto_delete=True)
                channel.queue_delete(queue=test_queue)

                connection.close()

                response_time = (time.time() - start_time) * 1000

                self.health_check_counter.labels(component="rabbitmq", status="ok").inc()
                self.component_status.labels(component="rabbitmq").set(1.0)

                return HealthCheckResult(
                    component="rabbitmq",
                    status="ok",
                    message="RabbitMQ connection successful",
                    response_time_ms=response_time,
                    details={"host": host, "port": port},
                )

            except Exception as e:
                response_time = (time.time() - start_time) * 1000

                self.health_check_counter.labels(component="rabbitmq", status="error").inc()
                self.component_status.labels(component="rabbitmq").set(0.0)

                return HealthCheckResult(
                    component="rabbitmq",
                    status="error",
                    message=f"RabbitMQ connection failed: {str(e)}",
                    response_time_ms=response_time,
                    details={"host": host, "port": port, "error": str(e)},
                )

    async def check_gateway_health(
        self, gateway_url: str, timeout: float = 5.0
    ) -> HealthCheckResult:
        """Check Gateway service health using managed HTTP client."""
        start_time = time.time()

        with self.health_check_duration.labels(component="gateway").time():
            try:
                from resource_manager import http_client
                
                async with http_client() as client:
                    response = await client.get(f"{gateway_url}/healthz", timeout=timeout)

                    response_time = (time.time() - start_time) * 1000

                    if response.status_code == 200:
                        self.health_check_counter.labels(component="gateway", status="ok").inc()
                        self.component_status.labels(component="gateway").set(1.0)

                        try:
                            data = response.json()
                            gateway_status = data.get("status", "unknown")
                        except:
                            gateway_status = "unknown"

                        return HealthCheckResult(
                            component="gateway",
                            status="ok",
                            message="Gateway service is healthy",
                            response_time_ms=response_time,
                            details={
                                "url": gateway_url,
                                "status_code": response.status_code,
                                "gateway_status": gateway_status,
                            },
                        )
                    else:
                        self.health_check_counter.labels(
                            component="gateway", status="warning"
                        ).inc()
                        self.component_status.labels(component="gateway").set(0.5)

                        return HealthCheckResult(
                            component="gateway",
                            status="warning",
                            message=f"Gateway returned status {response.status_code}",
                            response_time_ms=response_time,
                            details={"url": gateway_url, "status_code": response.status_code},
                        )

            except httpx.TimeoutException:
                response_time = (time.time() - start_time) * 1000

                self.health_check_counter.labels(component="gateway", status="error").inc()
                self.component_status.labels(component="gateway").set(0.0)

                return HealthCheckResult(
                    component="gateway",
                    status="error",
                    message="Gateway service timeout",
                    response_time_ms=response_time,
                    details={"url": gateway_url, "timeout": timeout},
                )

            except Exception as e:
                response_time = (time.time() - start_time) * 1000

                self.health_check_counter.labels(component="gateway", status="error").inc()
                self.component_status.labels(component="gateway").set(0.0)

                return HealthCheckResult(
                    component="gateway",
                    status="error",
                    message=f"Gateway check failed: {str(e)}",
                    response_time_ms=response_time,
                    details={"url": gateway_url, "error": str(e)},
                )

    async def check_ai_provider_health(
        self, provider: str, api_key: str, timeout: float = 10.0
    ) -> HealthCheckResult:
        """Check AI provider API health."""
        start_time = time.time()

        with self.health_check_duration.labels(component=f"ai_provider_{provider}").time():
            try:
                if provider.lower() == "openai":
                    url = "https://api.openai.com/v1/models"
                    headers = {"Authorization": f"Bearer {api_key}"}
                elif provider.lower() == "anthropic":
                    url = "https://api.anthropic.com/v1/models"
                    headers = {
                        "Authorization": f"Bearer {api_key}",
                        "anthropic-version": "2023-06-01",
                    }
                else:
                    raise ValueError(f"Unsupported provider: {provider}")

                from resource_manager import http_client
                
                async with http_client() as client:
                    response = await client.get(url, headers=headers, timeout=timeout)

                    response_time = (time.time() - start_time) * 1000

                    if response.status_code == 200:
                        self.health_check_counter.labels(
                            component=f"ai_provider_{provider}", status="ok"
                        ).inc()
                        self.component_status.labels(component=f"ai_provider_{provider}").set(1.0)

                        return HealthCheckResult(
                            component=f"ai_provider_{provider}",
                            status="ok",
                            message=f"{provider.title()} API is accessible",
                            response_time_ms=response_time,
                            details={"provider": provider, "status_code": response.status_code},
                        )
                    else:
                        self.health_check_counter.labels(
                            component=f"ai_provider_{provider}", status="error"
                        ).inc()
                        self.component_status.labels(component=f"ai_provider_{provider}").set(0.0)

                        return HealthCheckResult(
                            component=f"ai_provider_{provider}",
                            status="error",
                            message=f"{provider.title()} API returned status {response.status_code}",
                            response_time_ms=response_time,
                            details={
                                "provider": provider,
                                "status_code": response.status_code,
                                "response": response.text[:200],
                            },
                        )

            except Exception as e:
                response_time = (time.time() - start_time) * 1000

                self.health_check_counter.labels(
                    component=f"ai_provider_{provider}", status="error"
                ).inc()
                self.component_status.labels(component=f"ai_provider_{provider}").set(0.0)

                return HealthCheckResult(
                    component=f"ai_provider_{provider}",
                    status="error",
                    message=f"{provider.title()} API check failed: {str(e)}",
                    response_time_ms=response_time,
                    details={"provider": provider, "error": str(e)},
                )

    async def comprehensive_health_check(self) -> Dict[str, Any]:
        """Perform comprehensive health check of all components."""
        start_time = time.time()

        # Collect system metrics
        system_metrics = self.collect_system_metrics()

        # Prepare health check tasks
        health_checks = []

        # RabbitMQ health check
        rabbitmq_host = os.getenv("RABBITMQ_HOST", "localhost")
        rabbitmq_port = int(os.getenv("RABBITMQ_PORT", "5672"))
        rabbitmq_user = os.getenv("RABBITMQ_USER", "husky")
        rabbitmq_password = os.getenv("RABBITMQ_PASSWORD", "husky")

        health_checks.append(
            self.check_rabbitmq_health(
                host=rabbitmq_host,
                port=rabbitmq_port,
                username=rabbitmq_user,
                password=rabbitmq_password,
            )
        )

        # Gateway health check
        gateway_url = os.getenv("GATEWAY_INTERNAL_URL", "http://localhost:8080")
        health_checks.append(self.check_gateway_health(gateway_url))

        # AI provider health checks
        openai_key = os.getenv("OPENAI_API_KEY")
        if openai_key:
            health_checks.append(self.check_ai_provider_health("openai", openai_key))

        anthropic_key = os.getenv("ANTHROPIC_API_KEY")
        if anthropic_key:
            health_checks.append(self.check_ai_provider_health("anthropic", anthropic_key))

        # Execute all health checks concurrently
        health_results = await asyncio.gather(*health_checks, return_exceptions=True)

        # Process results
        components = {}
        total_checks = 0
        healthy_checks = 0

        for result in health_results:
            if isinstance(result, Exception):
                # Handle unexpected exceptions
                components["unknown_error"] = {
                    "status": "error",
                    "message": f"Health check failed: {str(result)}",
                }
                total_checks += 1
            elif isinstance(result, HealthCheckResult):
                components[result.component] = {
                    "status": result.status,
                    "message": result.message,
                    "response_time_ms": (
                        str(result.response_time_ms)
                        if result.response_time_ms is not None
                        else "N/A"
                    ),
                    "details": str(result.details) if result.details is not None else {},
                }
                total_checks += 1
                if result.status == "ok":
                    healthy_checks += 1
                elif result.status == "warning":
                    healthy_checks = int(healthy_checks + 0.5)

        # Calculate overall status
        if total_checks == 0:
            overall_status = "unknown"
        elif healthy_checks == total_checks:
            overall_status = "ok"
        elif healthy_checks >= total_checks * 0.7:
            overall_status = "degraded"
        else:
            overall_status = "unhealthy"

        # Check system resource thresholds
        resource_warnings = []
        if system_metrics.cpu_percent > 80:
            resource_warnings.append(f"High CPU usage: {system_metrics.cpu_percent:.1f}%")
        if system_metrics.memory_percent > 85:
            resource_warnings.append(f"High memory usage: {system_metrics.memory_percent:.1f}%")
        if system_metrics.disk_usage_percent > 90:
            resource_warnings.append(f"High disk usage: {system_metrics.disk_usage_percent:.1f}%")

        total_check_time = (time.time() - start_time) * 1000

        return {
            "status": overall_status,
            "timestamp": time.time(),
            "version": "0.0.1",
            "check_duration_ms": total_check_time,
            "components": components,
            "system_metrics": {
                "cpu_percent": system_metrics.cpu_percent,
                "memory_percent": system_metrics.memory_percent,
                "memory_used_mb": system_metrics.memory_used_mb,
                "memory_available_mb": system_metrics.memory_available_mb,
                "disk_usage_percent": system_metrics.disk_usage_percent,
                "uptime_seconds": system_metrics.uptime_seconds,
            },
            "resource_warnings": resource_warnings,
            "summary": {
                "total_components": total_checks,
                "healthy_components": healthy_checks,
                "health_score": (healthy_checks / total_checks * 100) if total_checks > 0 else 0,
            },
        }


# Global monitor instance
monitor = PerformanceMonitor()
