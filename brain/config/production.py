"""
HuskyApply Brain Service - Production Configuration
Optimized settings for production deployment with high performance, security, and reliability
"""

import logging
import os
from pathlib import Path
from typing import Any, Dict, List, Optional


class ProductionConfig:
    """Production configuration class for HuskyApply Brain Service."""

    # Application Configuration
    APP_NAME = "HuskyApply Brain Service"
    APP_VERSION = "2.0.0"
    APP_DESCRIPTION = "AI-powered cover letter generation service"

    # Environment
    ENVIRONMENT = "production"
    DEBUG = False
    TESTING = False

    # Server Configuration
    HOST = "0.0.0.0"
    PORT = int(os.getenv("PORT", "8000"))
    WORKERS = int(os.getenv("WORKERS", "4"))
    WORKER_CLASS = "uvicorn.workers.UvicornWorker"
    MAX_REQUESTS = int(os.getenv("MAX_REQUESTS", "1000"))
    MAX_REQUESTS_JITTER = int(os.getenv("MAX_REQUESTS_JITTER", "100"))
    TIMEOUT = int(os.getenv("TIMEOUT", "120"))
    KEEPALIVE = int(os.getenv("KEEPALIVE", "5"))

    # RabbitMQ Configuration - Production Cluster
    RABBITMQ_CLUSTER_ADDRESSES = os.getenv(
        "RABBITMQ_CLUSTER_ADDRESSES",
        "rabbitmq-1.internal:5672,rabbitmq-2.internal:5672,rabbitmq-3.internal:5672",
    ).split(",")
    RABBITMQ_USERNAME = os.getenv("RABBITMQ_USERNAME", "huskyapply_user")
    RABBITMQ_PASSWORD = os.getenv("RABBITMQ_PASSWORD")
    RABBITMQ_VIRTUAL_HOST = os.getenv("RABBITMQ_VIRTUAL_HOST", "/huskyapply")

    # Queue Configuration
    RABBITMQ_EXCHANGE = os.getenv("RABBITMQ_EXCHANGE", "huskyapply.jobs.exchange")
    RABBITMQ_QUEUE = os.getenv("RABBITMQ_QUEUE", "huskyapply.jobs.queue")
    RABBITMQ_ROUTING_KEY = os.getenv("RABBITMQ_ROUTING_KEY", "huskyapply.jobs.queue")
    RABBITMQ_DLQ_EXCHANGE = os.getenv("RABBITMQ_DLQ_EXCHANGE", "huskyapply.jobs.dlq.exchange")
    RABBITMQ_DLQ_QUEUE = os.getenv("RABBITMQ_DLQ_QUEUE", "huskyapply.jobs.dlq.queue")

    # RabbitMQ Connection Pool Settings
    RABBITMQ_CONNECTION_ATTEMPTS = int(os.getenv("RABBITMQ_CONNECTION_ATTEMPTS", "5"))
    RABBITMQ_RETRY_DELAY = int(os.getenv("RABBITMQ_RETRY_DELAY", "5"))
    RABBITMQ_HEARTBEAT = int(os.getenv("RABBITMQ_HEARTBEAT", "600"))
    RABBITMQ_BLOCKED_CONNECTION_TIMEOUT = int(
        os.getenv("RABBITMQ_BLOCKED_CONNECTION_TIMEOUT", "300")
    )
    RABBITMQ_PREFETCH_COUNT = int(os.getenv("RABBITMQ_PREFETCH_COUNT", "1"))

    # Gateway Configuration
    GATEWAY_INTERNAL_URL = os.getenv("GATEWAY_INTERNAL_URL", "http://gateway.internal:8080")
    INTERNAL_API_KEY = os.getenv("INTERNAL_API_KEY")
    GATEWAY_TIMEOUT = int(os.getenv("GATEWAY_TIMEOUT", "30"))
    GATEWAY_RETRY_ATTEMPTS = int(os.getenv("GATEWAY_RETRY_ATTEMPTS", "3"))
    GATEWAY_RETRY_DELAY = float(os.getenv("GATEWAY_RETRY_DELAY", "1.0"))

    # AI Model Configuration
    DEFAULT_AI_PROVIDER = os.getenv("DEFAULT_AI_PROVIDER", "openai")

    # OpenAI Configuration
    OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")
    OPENAI_API_BASE = os.getenv("OPENAI_API_BASE", "https://api.openai.com/v1")
    OPENAI_DEFAULT_MODEL = os.getenv("OPENAI_DEFAULT_MODEL", "gpt-4o")
    OPENAI_TEMPERATURE = float(os.getenv("OPENAI_TEMPERATURE", "0.7"))
    OPENAI_MAX_TOKENS = int(os.getenv("OPENAI_MAX_TOKENS", "1500"))
    OPENAI_TIMEOUT = int(os.getenv("OPENAI_TIMEOUT", "60"))
    OPENAI_MAX_RETRIES = int(os.getenv("OPENAI_MAX_RETRIES", "3"))

    # Anthropic Configuration
    ANTHROPIC_API_KEY = os.getenv("ANTHROPIC_API_KEY")
    ANTHROPIC_API_BASE = os.getenv("ANTHROPIC_API_BASE", "https://api.anthropic.com")
    ANTHROPIC_DEFAULT_MODEL = os.getenv("ANTHROPIC_DEFAULT_MODEL", "claude-3-5-sonnet-20241022")
    ANTHROPIC_MAX_TOKENS = int(os.getenv("ANTHROPIC_MAX_TOKENS", "1500"))
    ANTHROPIC_TIMEOUT = int(os.getenv("ANTHROPIC_TIMEOUT", "60"))

    # Web Scraping Configuration
    SCRAPING_TIMEOUT = int(os.getenv("SCRAPING_TIMEOUT", "30"))
    SCRAPING_MAX_RETRIES = int(os.getenv("SCRAPING_MAX_RETRIES", "3"))
    SCRAPING_RETRY_DELAY = float(os.getenv("SCRAPING_RETRY_DELAY", "2.0"))
    USER_AGENT = os.getenv("USER_AGENT", "HuskyApply/2.0.0 (+https://huskyapply.com/bot)")

    # Rate Limiting Configuration
    RATE_LIMIT_ENABLED = os.getenv("RATE_LIMIT_ENABLED", "true").lower() == "true"
    RATE_LIMIT_REQUESTS_PER_MINUTE = int(os.getenv("RATE_LIMIT_REQUESTS_PER_MINUTE", "60"))
    RATE_LIMIT_BURST_SIZE = int(os.getenv("RATE_LIMIT_BURST_SIZE", "10"))

    # Caching Configuration
    CACHE_ENABLED = os.getenv("CACHE_ENABLED", "true").lower() == "true"
    CACHE_TTL_SECONDS = int(os.getenv("CACHE_TTL_SECONDS", "3600"))
    CACHE_MAX_SIZE = int(os.getenv("CACHE_MAX_SIZE", "1000"))

    # Monitoring Configuration
    METRICS_ENABLED = os.getenv("METRICS_ENABLED", "true").lower() == "true"
    METRICS_PORT = int(os.getenv("METRICS_PORT", "9090"))
    PROMETHEUS_PUSHGATEWAY_URL = os.getenv("PROMETHEUS_PUSHGATEWAY_URL")
    PROMETHEUS_PUSHGATEWAY_ENABLED = bool(PROMETHEUS_PUSHGATEWAY_URL)

    # Tracing Configuration
    TRACING_ENABLED = os.getenv("TRACING_ENABLED", "true").lower() == "true"
    JAEGER_AGENT_HOST = os.getenv("JAEGER_AGENT_HOST", "jaeger-agent.monitoring")
    JAEGER_AGENT_PORT = int(os.getenv("JAEGER_AGENT_PORT", "6831"))
    TRACE_SAMPLING_RATE = float(os.getenv("TRACE_SAMPLING_RATE", "0.1"))

    # Logging Configuration
    LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
    LOG_FORMAT = os.getenv("LOG_FORMAT", "json")  # json or text
    LOG_FILE_PATH = os.getenv("LOG_FILE_PATH", "/var/log/huskyapply/brain.log")
    LOG_FILE_MAX_SIZE = os.getenv("LOG_FILE_MAX_SIZE", "100MB")
    LOG_FILE_BACKUP_COUNT = int(os.getenv("LOG_FILE_BACKUP_COUNT", "10"))

    # Structured Logging Fields
    LOG_INCLUDE_TRACE_ID = os.getenv("LOG_INCLUDE_TRACE_ID", "true").lower() == "true"
    LOG_INCLUDE_JOB_ID = os.getenv("LOG_INCLUDE_JOB_ID", "true").lower() == "true"
    LOG_INCLUDE_USER_ID = os.getenv("LOG_INCLUDE_USER_ID", "true").lower() == "true"

    # Health Check Configuration
    HEALTH_CHECK_TIMEOUT = int(os.getenv("HEALTH_CHECK_TIMEOUT", "10"))
    HEALTH_CHECK_RABBITMQ_ENABLED = (
        os.getenv("HEALTH_CHECK_RABBITMQ_ENABLED", "true").lower() == "true"
    )
    HEALTH_CHECK_GATEWAY_ENABLED = (
        os.getenv("HEALTH_CHECK_GATEWAY_ENABLED", "true").lower() == "true"
    )
    HEALTH_CHECK_AI_PROVIDERS_ENABLED = (
        os.getenv("HEALTH_CHECK_AI_PROVIDERS_ENABLED", "true").lower() == "true"
    )

    # Security Configuration
    CORS_ENABLED = os.getenv("CORS_ENABLED", "false").lower() == "true"
    CORS_ALLOWED_ORIGINS = (
        os.getenv("CORS_ALLOWED_ORIGINS", "").split(",")
        if os.getenv("CORS_ALLOWED_ORIGINS")
        else []
    )
    CORS_ALLOWED_METHODS = os.getenv("CORS_ALLOWED_METHODS", "GET,POST").split(",")
    CORS_ALLOWED_HEADERS = os.getenv("CORS_ALLOWED_HEADERS", "Content-Type,Authorization").split(
        ","
    )

    # Performance Configuration
    ASYNC_POOL_SIZE = int(os.getenv("ASYNC_POOL_SIZE", "100"))
    MAX_CONCURRENT_JOBS = int(os.getenv("MAX_CONCURRENT_JOBS", "10"))
    JOB_PROCESSING_TIMEOUT = int(os.getenv("JOB_PROCESSING_TIMEOUT", "300"))

    # Circuit Breaker Configuration
    CIRCUIT_BREAKER_ENABLED = os.getenv("CIRCUIT_BREAKER_ENABLED", "true").lower() == "true"
    CIRCUIT_BREAKER_FAILURE_THRESHOLD = int(os.getenv("CIRCUIT_BREAKER_FAILURE_THRESHOLD", "5"))
    CIRCUIT_BREAKER_RECOVERY_TIMEOUT = int(os.getenv("CIRCUIT_BREAKER_RECOVERY_TIMEOUT", "60"))
    CIRCUIT_BREAKER_EXPECTED_EXCEPTION = os.getenv(
        "CIRCUIT_BREAKER_EXPECTED_EXCEPTION", "Exception"
    )

    # Resource Limits
    MAX_MEMORY_MB = int(os.getenv("MAX_MEMORY_MB", "1024"))
    MAX_CPU_PERCENT = int(os.getenv("MAX_CPU_PERCENT", "80"))

    # Graceful Shutdown
    GRACEFUL_SHUTDOWN_TIMEOUT = int(os.getenv("GRACEFUL_SHUTDOWN_TIMEOUT", "30"))

    # Development/Debug Features (disabled in production)
    RELOAD = False
    ACCESS_LOG = False

    @classmethod
    def get_rabbitmq_url(cls) -> str:
        """Generate RabbitMQ connection URL."""
        # Use first address from cluster for primary connection
        primary_address = cls.RABBITMQ_CLUSTER_ADDRESSES[0]
        host = primary_address.split(":")[0]
        port = primary_address.split(":")[1] if ":" in primary_address else "5672"

        return f"amqp://{cls.RABBITMQ_USERNAME}:{cls.RABBITMQ_PASSWORD}@{host}:{port}{cls.RABBITMQ_VIRTUAL_HOST}"

    @classmethod
    def get_log_config(cls) -> Dict[str, Any]:
        """Generate logging configuration."""
        if cls.LOG_FORMAT.lower() == "json":
            formatter = {
                "format": '{"timestamp": "%(asctime)s", "level": "%(levelname)s", "service": "brain", "message": "%(message)s"'
                + (', "trace_id": "%(trace_id)s"' if cls.LOG_INCLUDE_TRACE_ID else "")
                + (', "job_id": "%(job_id)s"' if cls.LOG_INCLUDE_JOB_ID else "")
                + (', "user_id": "%(user_id)s"' if cls.LOG_INCLUDE_USER_ID else "")
                + "}",
                "class": "pythonjsonlogger.jsonlogger.JsonFormatter",
            }
        else:
            format_parts = ["%(asctime)s", "[%(levelname)s]", "service=brain", 'msg="%(message)s"']
            if cls.LOG_INCLUDE_TRACE_ID:
                format_parts.append("trace_id=%(trace_id)s")
            if cls.LOG_INCLUDE_JOB_ID:
                format_parts.append("job_id=%(job_id)s")
            if cls.LOG_INCLUDE_USER_ID:
                format_parts.append("user_id=%(user_id)s")

            formatter = {"format": " ".join(format_parts)}

        return {
            "version": 1,
            "disable_existing_loggers": False,
            "formatters": {"default": formatter},
            "handlers": {
                "console": {
                    "class": "logging.StreamHandler",
                    "formatter": "default",
                    "stream": "ext://sys.stdout",
                },
                "file": {
                    "class": "logging.handlers.RotatingFileHandler",
                    "formatter": "default",
                    "filename": cls.LOG_FILE_PATH,
                    "maxBytes": cls._parse_size(cls.LOG_FILE_MAX_SIZE),
                    "backupCount": cls.LOG_FILE_BACKUP_COUNT,
                },
            },
            "root": {"level": cls.LOG_LEVEL, "handlers": ["console", "file"]},
            "loggers": {
                "uvicorn": {
                    "level": "WARNING",
                    "handlers": ["console", "file"],
                    "propagate": False,
                },
                "httpx": {"level": "WARNING", "handlers": ["console", "file"], "propagate": False},
                "pika": {"level": "WARNING", "handlers": ["console", "file"], "propagate": False},
            },
        }

    @staticmethod
    def _parse_size(size_str: str) -> int:
        """Parse size string like '100MB' to bytes."""
        size_str = size_str.upper().strip()
        if size_str.endswith("KB"):
            return int(size_str[:-2]) * 1024
        elif size_str.endswith("MB"):
            return int(size_str[:-2]) * 1024 * 1024
        elif size_str.endswith("GB"):
            return int(size_str[:-2]) * 1024 * 1024 * 1024
        else:
            return int(size_str)

    @classmethod
    def validate_config(cls) -> List[str]:
        """Validate production configuration and return list of issues."""
        issues = []

        # Required environment variables
        required_vars = [
            ("RABBITMQ_PASSWORD", cls.RABBITMQ_PASSWORD),
            ("INTERNAL_API_KEY", cls.INTERNAL_API_KEY),
            ("OPENAI_API_KEY", cls.OPENAI_API_KEY),
        ]

        for var_name, var_value in required_vars:
            if not var_value:
                issues.append(f"Missing required environment variable: {var_name}")

        # Validate numeric ranges
        if cls.WORKERS < 1:
            issues.append("WORKERS must be >= 1")

        if cls.RABBITMQ_PREFETCH_COUNT < 1:
            issues.append("RABBITMQ_PREFETCH_COUNT must be >= 1")

        if cls.GATEWAY_TIMEOUT < 1:
            issues.append("GATEWAY_TIMEOUT must be >= 1")

        if cls.OPENAI_TEMPERATURE < 0 or cls.OPENAI_TEMPERATURE > 2:
            issues.append("OPENAI_TEMPERATURE must be between 0 and 2")

        if cls.OPENAI_MAX_TOKENS < 1 or cls.OPENAI_MAX_TOKENS > 4096:
            issues.append("OPENAI_MAX_TOKENS must be between 1 and 4096")

        # Validate log level
        valid_log_levels = ["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"]
        if cls.LOG_LEVEL not in valid_log_levels:
            issues.append(f"LOG_LEVEL must be one of: {', '.join(valid_log_levels)}")

        # Validate log format
        if cls.LOG_FORMAT.lower() not in ["json", "text"]:
            issues.append("LOG_FORMAT must be 'json' or 'text'")

        # Validate file paths
        log_dir = Path(cls.LOG_FILE_PATH).parent
        if not log_dir.exists():
            issues.append(f"Log directory does not exist: {log_dir}")

        return issues

    @classmethod
    def get_ai_provider_config(cls, provider: str) -> Dict[str, Any]:
        """Get configuration for specific AI provider."""
        if provider.lower() == "openai":
            return {
                "api_key": cls.OPENAI_API_KEY,
                "api_base": cls.OPENAI_API_BASE,
                "default_model": cls.OPENAI_DEFAULT_MODEL,
                "temperature": cls.OPENAI_TEMPERATURE,
                "max_tokens": cls.OPENAI_MAX_TOKENS,
                "timeout": cls.OPENAI_TIMEOUT,
                "max_retries": cls.OPENAI_MAX_RETRIES,
            }
        elif provider.lower() == "anthropic":
            return {
                "api_key": cls.ANTHROPIC_API_KEY,
                "api_base": cls.ANTHROPIC_API_BASE,
                "default_model": cls.ANTHROPIC_DEFAULT_MODEL,
                "max_tokens": cls.ANTHROPIC_MAX_TOKENS,
                "timeout": cls.ANTHROPIC_TIMEOUT,
            }
        else:
            raise ValueError(f"Unsupported AI provider: {provider}")


# Gunicorn configuration for production deployment
bind = f"{ProductionConfig.HOST}:{ProductionConfig.PORT}"
workers = ProductionConfig.WORKERS
worker_class = ProductionConfig.WORKER_CLASS
worker_connections = 1000
max_requests = ProductionConfig.MAX_REQUESTS
max_requests_jitter = ProductionConfig.MAX_REQUESTS_JITTER
timeout = ProductionConfig.TIMEOUT
keepalive = ProductionConfig.KEEPALIVE
preload_app = True
reload = ProductionConfig.RELOAD
accesslog = "-" if ProductionConfig.ACCESS_LOG else None
errorlog = "-"
loglevel = ProductionConfig.LOG_LEVEL.lower()
access_log_format = '%(h)s %(l)s %(u)s %(t)s "%(r)s" %(s)s %(b)s "%(f)s" "%(a)s" %(D)s'

# Graceful shutdown
graceful_timeout = ProductionConfig.GRACEFUL_SHUTDOWN_TIMEOUT

# Process naming
proc_name = "huskyapply-brain"


def when_ready(server: Any) -> None:
    """Called when the server is ready to accept connections."""
    issues = ProductionConfig.validate_config()
    if issues:
        server.log.error("Configuration validation failed:")
        for issue in issues:
            server.log.error(f"  - {issue}")
        # Don't exit in production, log warnings instead
        server.log.warning("Continuing with invalid configuration - please fix ASAP")
    else:
        server.log.info("Configuration validation passed")


def worker_int(worker: Any) -> None:
    """Called when a worker receives the INT or QUIT signal."""
    worker.log.info("Worker received INT/QUIT signal, shutting down gracefully")


def on_exit(server: Any) -> None:
    """Called when the server shuts down."""
    server.log.info("Server shutting down")
