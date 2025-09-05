"""
Resource management configuration for the HuskyApply Brain service.

This module provides configuration settings for resource management including
HTTP client pools, memory management, and cleanup intervals.
"""

import os
from dataclasses import dataclass, field
from typing import Dict, Any, Optional


@dataclass
class HTTPPoolConfig:
    """Configuration for HTTP client pooling."""
    
    max_pool_size: int = 10
    max_connections_per_host: int = 5
    timeout: float = 30.0
    max_keepalive_connections: int = 20
    keepalive_expiry: float = 5.0
    
    @classmethod
    def from_env(cls) -> "HTTPPoolConfig":
        """Create configuration from environment variables."""
        return cls(
            max_pool_size=int(os.getenv("HTTP_POOL_SIZE", "10")),
            max_connections_per_host=int(os.getenv("HTTP_MAX_CONNECTIONS_PER_HOST", "5")),
            timeout=float(os.getenv("HTTP_TIMEOUT", "30.0")),
            max_keepalive_connections=int(os.getenv("HTTP_MAX_KEEPALIVE_CONNECTIONS", "20")),
            keepalive_expiry=float(os.getenv("HTTP_KEEPALIVE_EXPIRY", "5.0")),
        )


@dataclass
class RabbitMQPoolConfig:
    """Configuration for RabbitMQ connection pooling."""
    
    max_connections: int = 3
    connection_timeout: float = 30.0
    heartbeat: int = 600
    blocked_connection_timeout: float = 300.0
    connection_attempts: int = 3
    retry_delay: int = 5
    
    @classmethod
    def from_env(cls) -> "RabbitMQPoolConfig":
        """Create configuration from environment variables."""
        return cls(
            max_connections=int(os.getenv("RABBITMQ_POOL_SIZE", "3")),
            connection_timeout=float(os.getenv("RABBITMQ_CONNECTION_TIMEOUT", "30.0")),
            heartbeat=int(os.getenv("RABBITMQ_HEARTBEAT", "600")),
            blocked_connection_timeout=float(os.getenv("RABBITMQ_BLOCKED_CONNECTION_TIMEOUT", "300.0")),
            connection_attempts=int(os.getenv("RABBITMQ_CONNECTION_ATTEMPTS", "3")),
            retry_delay=int(os.getenv("RABBITMQ_RETRY_DELAY", "5")),
        )


@dataclass
class MemoryConfig:
    """Configuration for memory management."""
    
    threshold_mb: float = 512.0
    cleanup_threshold: float = 0.8  # Trigger cleanup at 80% of threshold
    gc_interval: float = 60.0  # Garbage collection interval in seconds
    max_ai_objects: int = 100  # Maximum number of AI objects to track
    memory_monitor_interval: float = 30.0  # Memory monitoring interval
    
    # Alert thresholds
    memory_warning_threshold: float = 0.7  # Warning at 70%
    memory_critical_threshold: float = 0.9  # Critical at 90%
    
    @classmethod
    def from_env(cls) -> "MemoryConfig":
        """Create configuration from environment variables."""
        return cls(
            threshold_mb=float(os.getenv("MEMORY_THRESHOLD_MB", "512.0")),
            cleanup_threshold=float(os.getenv("MEMORY_CLEANUP_THRESHOLD", "0.8")),
            gc_interval=float(os.getenv("GC_INTERVAL", "60.0")),
            max_ai_objects=int(os.getenv("MAX_AI_OBJECTS", "100")),
            memory_monitor_interval=float(os.getenv("MEMORY_MONITOR_INTERVAL", "30.0")),
            memory_warning_threshold=float(os.getenv("MEMORY_WARNING_THRESHOLD", "0.7")),
            memory_critical_threshold=float(os.getenv("MEMORY_CRITICAL_THRESHOLD", "0.9")),
        )


@dataclass
class MonitoringConfig:
    """Configuration for resource monitoring and alerting."""
    
    enabled: bool = True
    stats_log_interval: float = 300.0  # Log stats every 5 minutes
    health_check_interval: float = 30.0  # Health check interval
    metrics_retention_hours: int = 24  # How long to keep metrics in memory
    
    # Alert configuration
    alert_cooldown: float = 300.0  # Minimum time between alerts
    webhook_timeout: float = 10.0  # Webhook timeout for alerts
    
    @classmethod
    def from_env(cls) -> "MonitoringConfig":
        """Create configuration from environment variables."""
        return cls(
            enabled=os.getenv("MONITORING_ENABLED", "true").lower() == "true",
            stats_log_interval=float(os.getenv("STATS_LOG_INTERVAL", "300.0")),
            health_check_interval=float(os.getenv("HEALTH_CHECK_INTERVAL", "30.0")),
            metrics_retention_hours=int(os.getenv("METRICS_RETENTION_HOURS", "24")),
            alert_cooldown=float(os.getenv("ALERT_COOLDOWN", "300.0")),
            webhook_timeout=float(os.getenv("WEBHOOK_TIMEOUT", "10.0")),
        )


@dataclass
class ShutdownConfig:
    """Configuration for graceful shutdown."""
    
    timeout: float = 30.0  # Maximum shutdown time
    force_timeout: float = 60.0  # Force shutdown timeout
    cleanup_timeout: float = 15.0  # Per-component cleanup timeout
    
    @classmethod
    def from_env(cls) -> "ShutdownConfig":
        """Create configuration from environment variables."""
        return cls(
            timeout=float(os.getenv("SHUTDOWN_TIMEOUT", "30.0")),
            force_timeout=float(os.getenv("FORCE_SHUTDOWN_TIMEOUT", "60.0")),
            cleanup_timeout=float(os.getenv("CLEANUP_TIMEOUT", "15.0")),
        )


@dataclass
class ResourceConfig:
    """Master configuration for all resource management."""
    
    http_pool: HTTPPoolConfig = field(default_factory=HTTPPoolConfig)
    rabbitmq_pool: RabbitMQPoolConfig = field(default_factory=RabbitMQPoolConfig)
    memory: MemoryConfig = field(default_factory=MemoryConfig)
    monitoring: MonitoringConfig = field(default_factory=MonitoringConfig)
    shutdown: ShutdownConfig = field(default_factory=ShutdownConfig)
    
    # Feature flags
    enable_connection_pooling: bool = True
    enable_memory_tracking: bool = True
    enable_resource_monitoring: bool = True
    enable_graceful_shutdown: bool = True
    
    @classmethod
    def from_env(cls) -> "ResourceConfig":
        """Create configuration from environment variables."""
        return cls(
            http_pool=HTTPPoolConfig.from_env(),
            rabbitmq_pool=RabbitMQPoolConfig.from_env(),
            memory=MemoryConfig.from_env(),
            monitoring=MonitoringConfig.from_env(),
            shutdown=ShutdownConfig.from_env(),
            enable_connection_pooling=os.getenv("ENABLE_CONNECTION_POOLING", "true").lower() == "true",
            enable_memory_tracking=os.getenv("ENABLE_MEMORY_TRACKING", "true").lower() == "true",
            enable_resource_monitoring=os.getenv("ENABLE_RESOURCE_MONITORING", "true").lower() == "true",
            enable_graceful_shutdown=os.getenv("ENABLE_GRACEFUL_SHUTDOWN", "true").lower() == "true",
        )
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert configuration to dictionary."""
        return {
            "http_pool": {
                "max_pool_size": self.http_pool.max_pool_size,
                "max_connections_per_host": self.http_pool.max_connections_per_host,
                "timeout": self.http_pool.timeout,
                "max_keepalive_connections": self.http_pool.max_keepalive_connections,
                "keepalive_expiry": self.http_pool.keepalive_expiry,
            },
            "rabbitmq_pool": {
                "max_connections": self.rabbitmq_pool.max_connections,
                "connection_timeout": self.rabbitmq_pool.connection_timeout,
                "heartbeat": self.rabbitmq_pool.heartbeat,
                "blocked_connection_timeout": self.rabbitmq_pool.blocked_connection_timeout,
                "connection_attempts": self.rabbitmq_pool.connection_attempts,
                "retry_delay": self.rabbitmq_pool.retry_delay,
            },
            "memory": {
                "threshold_mb": self.memory.threshold_mb,
                "cleanup_threshold": self.memory.cleanup_threshold,
                "gc_interval": self.memory.gc_interval,
                "max_ai_objects": self.memory.max_ai_objects,
                "memory_monitor_interval": self.memory.memory_monitor_interval,
                "memory_warning_threshold": self.memory.memory_warning_threshold,
                "memory_critical_threshold": self.memory.memory_critical_threshold,
            },
            "monitoring": {
                "enabled": self.monitoring.enabled,
                "stats_log_interval": self.monitoring.stats_log_interval,
                "health_check_interval": self.monitoring.health_check_interval,
                "metrics_retention_hours": self.monitoring.metrics_retention_hours,
                "alert_cooldown": self.monitoring.alert_cooldown,
                "webhook_timeout": self.monitoring.webhook_timeout,
            },
            "shutdown": {
                "timeout": self.shutdown.timeout,
                "force_timeout": self.shutdown.force_timeout,
                "cleanup_timeout": self.shutdown.cleanup_timeout,
            },
            "feature_flags": {
                "enable_connection_pooling": self.enable_connection_pooling,
                "enable_memory_tracking": self.enable_memory_tracking,
                "enable_resource_monitoring": self.enable_resource_monitoring,
                "enable_graceful_shutdown": self.enable_graceful_shutdown,
            },
        }
    
    def validate(self) -> None:
        """Validate configuration values."""
        errors = []
        
        # Validate HTTP pool configuration
        if self.http_pool.max_pool_size <= 0:
            errors.append("HTTP pool size must be positive")
        if self.http_pool.timeout <= 0:
            errors.append("HTTP timeout must be positive")
        if self.http_pool.max_connections_per_host <= 0:
            errors.append("Max connections per host must be positive")
        
        # Validate RabbitMQ configuration
        if self.rabbitmq_pool.max_connections <= 0:
            errors.append("RabbitMQ pool size must be positive")
        if self.rabbitmq_pool.heartbeat < 0:
            errors.append("RabbitMQ heartbeat must be non-negative")
        
        # Validate memory configuration
        if self.memory.threshold_mb <= 0:
            errors.append("Memory threshold must be positive")
        if not 0 < self.memory.cleanup_threshold <= 1:
            errors.append("Memory cleanup threshold must be between 0 and 1")
        if self.memory.gc_interval <= 0:
            errors.append("GC interval must be positive")
        
        # Validate monitoring configuration
        if self.monitoring.stats_log_interval <= 0:
            errors.append("Stats log interval must be positive")
        if self.monitoring.health_check_interval <= 0:
            errors.append("Health check interval must be positive")
        
        # Validate shutdown configuration
        if self.shutdown.timeout <= 0:
            errors.append("Shutdown timeout must be positive")
        if self.shutdown.force_timeout <= self.shutdown.timeout:
            errors.append("Force shutdown timeout must be greater than shutdown timeout")
        
        if errors:
            raise ValueError(f"Configuration validation failed: {', '.join(errors)}")


# Global configuration instance
_config: Optional[ResourceConfig] = None


def get_resource_config() -> ResourceConfig:
    """Get the global resource configuration instance."""
    global _config
    if _config is None:
        _config = ResourceConfig.from_env()
        _config.validate()
    return _config


def reset_config() -> None:
    """Reset the global configuration (mainly for testing)."""
    global _config
    _config = None