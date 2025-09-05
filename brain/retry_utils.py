"""
Retry utilities for robust error handling in the Brain service.

This module provides decorators and utilities for implementing
exponential backoff and retry logic for external service calls.
"""

import functools
import logging
import time
from typing import Any, Callable, Dict, Optional, Tuple, Type, Union

import httpx

logger = logging.getLogger(__name__)


def exponential_backoff_retry(
    max_retries: int = 3,
    base_delay: float = 1.0,
    max_delay: float = 60.0,
    backoff_multiplier: float = 2.0,
    exceptions: Union[Type[Exception], Tuple[Type[Exception], ...]] = Exception,
    on_retry: Optional[Callable[[int, Exception, float], None]] = None,
) -> Callable:
    """
    Decorator for exponential backoff retry logic.

    Args:
        max_retries: Maximum number of retry attempts
        base_delay: Initial delay between retries in seconds
        max_delay: Maximum delay between retries in seconds
        backoff_multiplier: Multiplier for exponential backoff
        exceptions: Exception types to catch and retry
        on_retry: Optional callback function called on each retry
    """

    def decorator(func: Callable) -> Callable:
        @functools.wraps(func)
        def wrapper(*args: Any, **kwargs: Any) -> Any:
            last_exception = None

            for attempt in range(max_retries + 1):
                try:
                    return func(*args, **kwargs)
                except exceptions as e:
                    last_exception = e

                    if attempt == max_retries:
                        # Last attempt failed, raise the exception
                        logger.error(
                            f"Function {func.__name__} failed after {max_retries + 1} attempts: {e}"
                        )
                        raise e

                    # Calculate delay with exponential backoff
                    delay = min(base_delay * (backoff_multiplier**attempt), max_delay)

                    logger.warning(
                        f"Function {func.__name__} failed on attempt {attempt + 1}/{max_retries + 1}: {e}. "
                        f"Retrying in {delay:.2f} seconds..."
                    )

                    # Call retry callback if provided
                    if on_retry:
                        on_retry(attempt + 1, e, delay)

                    time.sleep(delay)

            # This should never be reached, but just in case
            if last_exception:
                raise last_exception

        return wrapper

    return decorator


async def notify_gateway_with_retry(
    gateway_url: str,
    job_id: str,
    payload: Dict[str, Any],
    headers: Dict[str, str],
    max_retries: int = 3,
) -> bool:
    """
    Notify Gateway with retry logic for robustness using resource-managed HTTP clients.

    Args:
        gateway_url: Gateway notification URL
        job_id: Job ID for logging
        payload: Notification payload
        headers: HTTP headers
        max_retries: Maximum retry attempts

    Returns:
        bool: True if notification succeeded, False otherwise
    """
    from resource_manager import http_client

    @exponential_backoff_retry(
        max_retries=max_retries,
        base_delay=1.0,
        max_delay=10.0,
        exceptions=(httpx.RequestError, httpx.HTTPStatusError),
        on_retry=lambda attempt, error, delay: logger.info(
            f"Retrying Gateway notification for job {job_id}, attempt {attempt}, delay {delay:.2f}s"
        ),
    )
    async def _make_request() -> httpx.Response:
        async with http_client() as client:
            response = await client.post(gateway_url, json=payload, headers=headers)
            response.raise_for_status()
            return response

    try:
        await _make_request()
        logger.info(f"Successfully notified Gateway for job {job_id}")
        return True
    except Exception as e:
        logger.error(
            f"Failed to notify Gateway for job {job_id} after {max_retries + 1} attempts: {e}"
        )
        return False


def notify_gateway_with_retry_sync(
    gateway_url: str,
    job_id: str,
    payload: Dict[str, Any],
    headers: Dict[str, str],
    max_retries: int = 3,
) -> bool:
    """
    Synchronous version of Gateway notification with retry logic.
    
    This is a wrapper around the async version for backward compatibility.
    """
    import asyncio
    
    try:
        loop = asyncio.get_event_loop()
        return loop.run_until_complete(
            notify_gateway_with_retry(gateway_url, job_id, payload, headers, max_retries)
        )
    except RuntimeError:
        # No event loop running, create a new one
        return asyncio.run(
            notify_gateway_with_retry(gateway_url, job_id, payload, headers, max_retries)
        )


class CircuitBreaker:
    """
    Circuit breaker pattern implementation for external service calls.

    This helps prevent cascading failures by temporarily stopping
    calls to failing services.
    """

    def __init__(
        self,
        failure_threshold: int = 5,
        timeout: float = 60.0,
        expected_exception: Type[Exception] = Exception,
    ) -> None:
        self.failure_threshold = failure_threshold
        self.timeout = timeout
        self.expected_exception = expected_exception

        self.failure_count = 0
        self.last_failure_time: Optional[float] = None
        self.state = "CLOSED"  # CLOSED, OPEN, HALF_OPEN

    def call(self, func: Callable[..., Any], *args: Any, **kwargs: Any) -> Any:
        """
        Execute function with circuit breaker protection.
        """
        if self.state == "OPEN":
            if self.last_failure_time and time.time() - self.last_failure_time < self.timeout:
                raise Exception(f"Circuit breaker is OPEN. Service unavailable.")
            else:
                self.state = "HALF_OPEN"

        try:
            result = func(*args, **kwargs)
            self._on_success()
            return result
        except self.expected_exception as e:
            self._on_failure()
            raise e

    def _on_success(self) -> None:
        """Reset circuit breaker on successful call."""
        self.failure_count = 0
        self.state = "CLOSED"

    def _on_failure(self) -> None:
        """Handle failure and potentially open circuit."""
        self.failure_count += 1
        self.last_failure_time = time.time()

        if self.failure_count >= self.failure_threshold:
            self.state = "OPEN"
            logger.warning(
                f"Circuit breaker opened after {self.failure_count} failures. "
                f"Will retry after {self.timeout} seconds."
            )


# Global circuit breaker for Gateway notifications
gateway_circuit_breaker = CircuitBreaker(
    failure_threshold=3,
    timeout=30.0,
    expected_exception=Exception,  # Accept any exception for flexibility
)
