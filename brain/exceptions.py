"""
Custom exceptions for the HuskyApply Brain service.

This module defines specific exception classes for different error scenarios
to enable better error handling and monitoring.
"""

from typing import Any, Dict, Optional


class BrainServiceException(Exception):
    """Base exception class for Brain service errors."""

    def __init__(
        self,
        message: str,
        error_code: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
    ):
        self.message = message
        self.error_code = error_code or self.__class__.__name__
        self.details = details or {}
        super().__init__(self.message)

    def to_dict(self) -> Dict[str, Any]:
        """Convert exception to dictionary for JSON serialization."""
        return {"error_code": self.error_code, "message": self.message, "details": self.details}


class JobProcessingException(BrainServiceException):
    """Exception raised during job processing."""

    pass


class AIProviderException(BrainServiceException):
    """Exception raised when AI provider calls fail."""

    def __init__(
        self,
        message: str,
        provider: str,
        model: Optional[str] = None,
        error_code: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
    ):
        self.provider = provider
        self.model = model
        details = details or {}
        details.update({"provider": provider, "model": model})
        super().__init__(message, error_code, details)


class WebScrapingException(BrainServiceException):
    """Exception raised during web scraping operations."""

    def __init__(
        self,
        message: str,
        url: str,
        status_code: Optional[int] = None,
        error_code: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
    ):
        self.url = url
        self.status_code = status_code
        details = details or {}
        details.update({"url": url, "status_code": status_code})
        super().__init__(message, error_code, details)


class GatewayException(BrainServiceException):
    """Exception raised when Gateway communication fails."""

    def __init__(
        self,
        message: str,
        job_id: str,
        gateway_url: str,
        error_code: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
    ):
        self.job_id = job_id
        self.gateway_url = gateway_url
        details = details or {}
        details.update({"job_id": job_id, "gateway_url": gateway_url})
        super().__init__(message, error_code, details)


class RabbitMQException(BrainServiceException):
    """Exception raised for RabbitMQ connection or messaging issues."""

    def __init__(
        self,
        message: str,
        operation: str,
        queue_name: Optional[str] = None,
        error_code: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
    ):
        self.operation = operation
        self.queue_name = queue_name
        details = details or {}
        details.update({"operation": operation, "queue_name": queue_name})
        super().__init__(message, error_code, details)


class ConfigurationException(BrainServiceException):
    """Exception raised for configuration errors."""

    def __init__(
        self,
        message: str,
        config_key: str,
        error_code: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
    ):
        self.config_key = config_key
        details = details or {}
        details.update({"config_key": config_key})
        super().__init__(message, error_code, details)


class ValidationException(BrainServiceException):
    """Exception raised for data validation errors."""

    def __init__(
        self,
        message: str,
        field_name: str,
        field_value: Any = None,
        error_code: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
    ):
        self.field_name = field_name
        self.field_value = field_value
        details = details or {}
        details.update({"field_name": field_name, "field_value": field_value})
        super().__init__(message, error_code, details)
