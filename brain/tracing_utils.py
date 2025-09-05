"""
Tracing utilities for distributed tracing in the Brain service.

This module provides utilities for correlation ID management
and distributed tracing across microservices.
"""

import json
import logging
import uuid
from contextvars import ContextVar, Token
from typing import Any, Dict, Optional, Union

from pika.spec import BasicProperties

# Context variables for tracing information
trace_context: ContextVar[Dict[str, Any]] = ContextVar("trace_context", default={})

logger = logging.getLogger(__name__)


class TraceContext:
    """
    Context manager for distributed tracing information.

    This class manages trace IDs, span IDs, and other tracing
    information throughout the request lifecycle.
    """

    def __init__(
        self,
        trace_id: Optional[str] = None,
        parent_span_id: Optional[str] = None,
        job_id: Optional[str] = None,
        user_id: Optional[str] = None,
        operation: Optional[str] = None,
    ) -> None:
        self.trace_id = trace_id or str(uuid.uuid4())
        self.span_id = str(uuid.uuid4())[:8]
        self.parent_span_id = parent_span_id
        self.job_id = job_id
        self.user_id = user_id
        self.operation = operation or "unknown"
        self.service = "brain"

    def __enter__(self) -> "TraceContext":
        """Enter the trace context."""
        context_data = {
            "trace_id": self.trace_id,
            "span_id": self.span_id,
            "parent_span_id": self.parent_span_id,
            "job_id": self.job_id,
            "user_id": self.user_id,
            "operation": self.operation,
            "service": self.service,
        }

        # Set context variable
        self.token: Token[Dict[str, Any]] = trace_context.set(context_data)

        # Update logging context
        self._update_logging_context()

        logger.info(f"Started operation: {self.operation}", extra=self.get_logging_extra())
        return self

    def __exit__(
        self, exc_type: Optional[type], exc_val: Optional[Exception], exc_tb: Optional[Any]
    ) -> None:
        """Exit the trace context."""
        if exc_type:
            logger.error(
                f"Operation {self.operation} failed: {exc_val}", extra=self.get_logging_extra()
            )
        else:
            logger.info(f"Completed operation: {self.operation}", extra=self.get_logging_extra())

        # Reset context variable
        trace_context.reset(self.token)

    def get_logging_extra(self) -> Dict[str, Any]:
        """Get extra fields for logging."""
        return {
            "trace_id": self.trace_id,
            "span_id": self.span_id,
            "parent_span_id": self.parent_span_id,
            "job_id": self.job_id,
            "user_id": self.user_id,
            "service": self.service,
            "operation": self.operation,
        }

    def get_headers(self) -> Dict[str, str]:
        """Get HTTP headers for downstream service calls."""
        headers = {
            "X-Trace-ID": self.trace_id,
            "X-Span-ID": self.span_id,
            "X-Service": self.service,
        }

        if self.job_id:
            headers["X-Job-ID"] = self.job_id
        if self.user_id:
            headers["X-User-ID"] = self.user_id

        return headers

    def create_child_span(self, operation: str) -> "TraceContext":
        """Create a child span for sub-operations."""
        return TraceContext(
            trace_id=self.trace_id,
            parent_span_id=self.span_id,
            job_id=self.job_id,
            user_id=self.user_id,
            operation=operation,
        )

    def _update_logging_context(self) -> None:
        """Update the logging context with trace information."""
        # This would typically update MDC or similar logging context
        pass


def get_current_trace_context() -> Optional[Dict[str, Any]]:
    """Get the current trace context."""
    try:
        return trace_context.get()
    except LookupError:
        return None


def create_trace_from_headers(
    headers: Dict[str, str], job_id: Optional[str] = None, operation: Optional[str] = None
) -> TraceContext:
    """Create a trace context from HTTP headers."""
    return TraceContext(
        trace_id=headers.get("X-Trace-ID"),
        parent_span_id=headers.get("X-Span-ID"),
        job_id=job_id or headers.get("X-Job-ID"),
        user_id=headers.get("X-User-ID"),
        operation=operation,
    )


def create_trace_from_rabbitmq_properties(
    properties: Optional[BasicProperties],
    job_id: Optional[str] = None,
    operation: Optional[str] = None,
) -> TraceContext:
    """Create a trace context from RabbitMQ message properties."""
    headers: Dict[str, Optional[str]] = {}
    if properties and properties.headers:
        headers = {
            "X-Trace-ID": str(properties.headers.get("X-Trace-ID")) if properties.headers.get("X-Trace-ID") is not None else None,
            "X-Span-ID": str(properties.headers.get("X-Span-ID")) if properties.headers.get("X-Span-ID") is not None else None,
            "X-Job-ID": str(properties.headers.get("X-Job-ID")) if properties.headers.get("X-Job-ID") is not None else None,
            "X-User-ID": str(properties.headers.get("X-User-ID")) if properties.headers.get("X-User-ID") is not None else None,
        }

    return TraceContext(
        trace_id=headers.get("X-Trace-ID"),
        parent_span_id=headers.get("X-Span-ID"),
        job_id=job_id or headers.get("X-Job-ID"),
        user_id=headers.get("X-User-ID"),
        operation=operation or "message_processing",
    )


class TracedLogger:
    """
    Logger wrapper that includes tracing information automatically.
    """

    def __init__(self, logger_name: str) -> None:
        self.logger = logging.getLogger(logger_name)

    def _get_trace_extra(self) -> Dict[str, Any]:
        """Get tracing information for logging."""
        context = get_current_trace_context()
        if context:
            return {k: v for k, v in context.items() if v is not None}
        return {}

    def info(self, msg: str, *args: Any, **kwargs: Any) -> None:
        extra = kwargs.pop("extra", {})
        extra.update(self._get_trace_extra())
        self.logger.info(msg, *args, extra=extra, **kwargs)

    def warning(self, msg: str, *args: Any, **kwargs: Any) -> None:
        extra = kwargs.pop("extra", {})
        extra.update(self._get_trace_extra())
        self.logger.warning(msg, *args, extra=extra, **kwargs)

    def error(self, msg: str, *args: Any, **kwargs: Any) -> None:
        extra = kwargs.pop("extra", {})
        extra.update(self._get_trace_extra())
        self.logger.error(msg, *args, extra=extra, **kwargs)

    def debug(self, msg: str, *args: Any, **kwargs: Any) -> None:
        extra = kwargs.pop("extra", {})
        extra.update(self._get_trace_extra())
        self.logger.debug(msg, *args, extra=extra, **kwargs)


# Global traced logger instance
traced_logger = TracedLogger(__name__)
