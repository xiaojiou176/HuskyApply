"""
Streaming Handler Module for HuskyApply Brain Service

This module provides the core infrastructure for streaming AI responses to the Gateway.
It supports both Server-Sent Events (SSE) via HTTP and gRPC streaming.
"""

import asyncio
import logging
import json
import time
from enum import Enum
from dataclasses import dataclass, field
from typing import AsyncGenerator, Dict, Any, Optional, List, Union

logger = logging.getLogger(__name__)

class StreamingMode(Enum):
    """Supported streaming modes."""
    SSE = "sse"
    GRPC = "grpc"
    HYBRID = "hybrid"
    DISABLED = "disabled"

@dataclass
class StreamingConfig:
    """Configuration for streaming behavior."""
    mode: StreamingMode = StreamingMode.SSE
    chunk_size: int = 10
    update_interval_ms: int = 100
    enable_compression: bool = True
    include_metadata: bool = True
    max_buffer_size: int = 1000

class StreamingHandler:
    """
    Handles the streaming of AI generated content to the Gateway service.
    """

    def __init__(self, gateway_url: str, api_key: str, config: StreamingConfig):
        self.gateway_url = gateway_url
        self.api_key = api_key
        self.config = config
        self._buffer = []
        self._last_update_time = 0
        logger.info(f"StreamingHandler initialized in {config.mode.value} mode")

    async def stream_response(
        self,
        job_id: str,
        content_generator: AsyncGenerator[str, None],
        metadata: Optional[Dict[str, Any]] = None
    ) -> AsyncGenerator[Dict[str, Any], None]:
        """
        Stream content from a generator, buffering and formatting as needed.

        Args:
            job_id: The ID of the job being processed
            content_generator: Async generator yielding content chunks
            metadata: Optional metadata to include in the stream

        Yields:
            Formatted stream events
        """
        start_time = time.time()
        accumulated_text = ""
        chunk_count = 0

        try:
            async for chunk in content_generator:
                accumulated_text += chunk
                self._buffer.append(chunk)
                chunk_count += 1

                # Check if we should emit a buffer update
                current_time = time.time() * 1000
                time_diff = current_time - self._last_update_time

                if (len(self._buffer) >= self.config.chunk_size or
                    time_diff >= self.config.update_interval_ms):

                    payload = self._create_payload(
                        job_id,
                        "".join(self._buffer),
                        status="PROCESSING",
                        metadata=metadata
                    )

                    self._buffer = []
                    self._last_update_time = current_time
                    yield payload

            # Flush remaining buffer
            if self._buffer:
                payload = self._create_payload(
                    job_id,
                    "".join(self._buffer),
                    status="PROCESSING",
                    metadata=metadata
                )
                yield payload

            # Send completion event
            final_payload = self._create_payload(
                job_id,
                "",
                status="COMPLETED",
                metadata={
                    **(metadata or {}),
                    "total_time_ms": int((time.time() - start_time) * 1000),
                    "total_chunks": chunk_count,
                    "total_length": len(accumulated_text)
                }
            )
            yield final_payload

        except Exception as e:
            logger.error(f"Error during streaming for job {job_id}: {str(e)}")
            error_payload = self._create_payload(
                job_id,
                str(e),
                status="FAILED",
                is_error=True
            )
            yield error_payload

    def _create_payload(
        self,
        job_id: str,
        content: str,
        status: str,
        metadata: Optional[Dict[str, Any]] = None,
        is_error: bool = False
    ) -> Dict[str, Any]:
        """Create a standardized payload for the stream."""
        return {
            "job_id": job_id,
            "content": content,
            "status": status,
            "timestamp": time.time(),
            "metadata": metadata or {},
            "error": is_error
        }

    async def send_progress_update(self, job_id: str, progress: int, message: str):
        """Send a progress update event (non-content)."""
        # In a real implementation, this would send an HTTP request or gRPC message
        # to the Gateway. For now, we'll just log it.
        logger.info(f"Progress update for {job_id}: {progress}% - {message}")


# Singleton instance management
_streaming_handler: Optional[StreamingHandler] = None

async def configure_streaming(
    gateway_url: str,
    api_key: str,
    config: StreamingConfig
) -> StreamingHandler:
    """Initialize and configure the global streaming handler."""
    global _streaming_handler
    _streaming_handler = StreamingHandler(gateway_url, api_key, config)
    return _streaming_handler

def get_streaming_handler() -> StreamingHandler:
    """Get the global streaming handler instance."""
    global _streaming_handler
    if _streaming_handler is None:
        # Return a default handler if not configured (e.g. during tests)
        logger.warning("Streaming handler not configured, using default")
        return StreamingHandler(
            "http://localhost:8080",
            "default-key",
            StreamingConfig(mode=StreamingMode.DISABLED)
        )
    return _streaming_handler
