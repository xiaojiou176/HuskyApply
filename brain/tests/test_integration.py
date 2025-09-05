"""
Integration tests for the HuskyApply Brain service.
Tests the complete flow from RabbitMQ message consumption to AI processing.
"""

import json
import time
from typing import Any, Dict, Optional
from unittest.mock import AsyncMock, Mock, patch

import httpx
import pika
import pytest

from ai_chain import create_cover_letter_chain, scrape_jd_text
from main import RabbitMQConsumer, app
from retry_utils import notify_gateway_with_retry
from tracing_utils import TraceContext, TracedLogger


class TestBrainServiceIntegration:
    """Integration tests for the Brain service."""

    def setup_method(self) -> None:
        """Set up test fixtures."""
        self.mock_job_data: Dict[str, Any] = {
            "jobId": "test-job-123",
            "jdUrl": "https://example.com/job",
            "resumeUri": "s3://bucket/resume.pdf",
            "preferredModel": "gpt-4o",
            "trace_id": "trace-123",
            "user_id": "user-456",
        }

        self.expected_cover_letter: str = """Dear Hiring Manager,

I am writing to express my strong interest in the Software Engineer position at TechCorp. 
With my background in Python and Java development, I am excited about the opportunity 
to contribute to your innovative team.

My experience includes:
- 5+ years of full-stack development
- Expertise in microservices architecture  
- Strong problem-solving and collaboration skills

I would welcome the opportunity to discuss how my skills and passion for technology 
can contribute to TechCorp's continued success.

Best regards,
John Doe"""

    @pytest.mark.asyncio
    async def test_complete_job_processing_flow(self) -> None:
        """Test the complete flow from message consumption to callback."""
        consumer = RabbitMQConsumer()

        # Mock external dependencies
        with (
            patch("ai_chain.scrape_jd_text") as mock_scrape,
            patch("ai_chain.create_cover_letter_chain") as mock_chain,
            patch("retry_utils.notify_gateway_with_retry") as mock_notify,
        ):

            # Setup mocks
            mock_scrape.return_value = (
                "Software Engineer position at TechCorp. 5+ years Python experience required."
            )

            mock_ai_chain = Mock()
            mock_ai_chain.invoke.return_value = {"cover_letter": self.expected_cover_letter}
            mock_chain.return_value = mock_ai_chain

            mock_notify.return_value = True

            # Create mock message
            mock_channel = Mock()
            mock_method = Mock()
            mock_method.delivery_tag = "test-tag"
            mock_properties = Mock()
            mock_properties.headers = {"trace_id": "trace-123", "user_id": "user-456"}

            message_body = json.dumps(self.mock_job_data).encode("utf-8")

            # Process the message
            consumer.message_callback(mock_channel, mock_method, mock_properties, message_body)

            # Verify the flow
            mock_scrape.assert_called_once_with("https://example.com/job")
            mock_chain.assert_called_once()
            mock_ai_chain.invoke.assert_called_once()

            # Verify callback to gateway
            mock_notify.assert_called()
            callback_args = mock_notify.call_args[0]
            assert "test-job-123" in callback_args[0]  # URL contains job ID
            assert callback_args[1]["status"] == "COMPLETED"
            assert "cover_letter" in callback_args[1]

            # Verify message acknowledgment
            mock_channel.basic_ack.assert_called_once_with(delivery_tag="test-tag")

    @pytest.mark.asyncio
    async def test_job_processing_with_scraping_failure(self) -> None:
        """Test handling of web scraping failures."""
        consumer = RabbitMQConsumer()

        with (
            patch("ai_chain.scrape_jd_text") as mock_scrape,
            patch("retry_utils.notify_gateway_with_retry") as mock_notify,
        ):

            # Setup scraping failure
            mock_scrape.side_effect = Exception("Failed to scrape job description")
            mock_notify.return_value = True

            # Create mock message
            mock_channel = Mock()
            mock_method = Mock()
            mock_method.delivery_tag = "test-tag"
            mock_properties = Mock()
            mock_properties.headers = {"trace_id": "trace-123"}

            message_body = json.dumps(self.mock_job_data).encode("utf-8")

            # Process the message
            consumer.message_callback(mock_channel, mock_method, mock_properties, message_body)

            # Verify error notification
            mock_notify.assert_called()
            callback_args = mock_notify.call_args[0]
            assert callback_args[1]["status"] == "FAILED"
            assert "error" in callback_args[1]

            # Message should still be acknowledged
            mock_channel.basic_ack.assert_called_once_with(delivery_tag="test-tag")

    @pytest.mark.asyncio
    async def test_ai_processing_failure_handling(self) -> None:
        """Test handling of AI model failures."""
        consumer = RabbitMQConsumer()

        with (
            patch("ai_chain.scrape_jd_text") as mock_scrape,
            patch("ai_chain.create_cover_letter_chain") as mock_chain,
            patch("retry_utils.notify_gateway_with_retry") as mock_notify,
        ):

            # Setup mocks
            mock_scrape.return_value = "Job description content"

            mock_ai_chain = Mock()
            mock_ai_chain.invoke.side_effect = Exception("AI model temporarily unavailable")
            mock_chain.return_value = mock_ai_chain

            mock_notify.return_value = True

            # Create mock message
            mock_channel = Mock()
            mock_method = Mock()
            mock_method.delivery_tag = "test-tag"
            mock_properties = Mock()
            mock_properties.headers = {"trace_id": "trace-123"}

            message_body = json.dumps(self.mock_job_data).encode("utf-8")

            # Process the message
            consumer.message_callback(mock_channel, mock_method, mock_properties, message_body)

            # Verify error handling
            mock_notify.assert_called()
            callback_args = mock_notify.call_args[0]
            assert callback_args[1]["status"] == "FAILED"
            assert "AI model temporarily unavailable" in callback_args[1]["error"]

    @pytest.mark.asyncio
    async def test_gateway_callback_failure_handling(self) -> None:
        """Test handling of gateway callback failures."""
        consumer = RabbitMQConsumer()

        with (
            patch("ai_chain.scrape_jd_text") as mock_scrape,
            patch("ai_chain.create_cover_letter_chain") as mock_chain,
            patch("retry_utils.notify_gateway_with_retry") as mock_notify,
        ):

            # Setup successful processing but failed callback
            mock_scrape.return_value = "Job description"
            mock_ai_chain = Mock()
            mock_ai_chain.invoke.return_value = {"cover_letter": self.expected_cover_letter}
            mock_chain.return_value = mock_ai_chain

            # Simulate callback failure
            mock_notify.return_value = False

            # Create mock message
            mock_channel = Mock()
            mock_method = Mock()
            mock_method.delivery_tag = "test-tag"
            mock_properties = Mock()
            mock_properties.headers = {"trace_id": "trace-123"}

            message_body = json.dumps(self.mock_job_data).encode("utf-8")

            # Process the message
            consumer.message_callback(mock_channel, mock_method, mock_properties, message_body)

            # Message should still be acknowledged even if callback fails
            mock_channel.basic_ack.assert_called_once_with(delivery_tag="test-tag")

    def test_different_ai_models(self) -> None:
        """Test processing with different AI model configurations."""
        test_cases = [
            {"preferredModel": "gpt-4o", "expected_provider": "openai"},
            {"preferredModel": "gpt-3.5-turbo", "expected_provider": "openai"},
            {"preferredModel": "claude-3-opus-20240229", "expected_provider": "anthropic"},
            {"preferredModel": "", "expected_provider": "openai"},  # Default
        ]

        for case in test_cases:
            with patch("ai_chain.create_cover_letter_chain") as mock_chain:
                job_data = {**self.mock_job_data, "preferredModel": case["preferredModel"]}

                # This would be called during processing - fix function signature
                create_cover_letter_chain(
                    model_provider="openai",
                    model_name=case["preferredModel"],
                )

                # Verify the chain was created (implementation details would vary)
                mock_chain.assert_called_once()

    @pytest.mark.asyncio
    async def test_tracing_context_propagation(self) -> None:
        """Test that tracing context is properly propagated."""
        consumer = RabbitMQConsumer()

        with (
            patch("ai_chain.scrape_jd_text") as mock_scrape,
            patch("ai_chain.create_cover_letter_chain") as mock_chain,
            patch("retry_utils.notify_gateway_with_retry") as mock_notify,
            patch("tracing_utils.TracedLogger") as mock_logger,
        ):

            # Setup mocks
            mock_scrape.return_value = "Job description"
            mock_ai_chain = Mock()
            mock_ai_chain.invoke.return_value = {"cover_letter": self.expected_cover_letter}
            mock_chain.return_value = mock_ai_chain
            mock_notify.return_value = True

            # Create message with tracing headers
            mock_channel = Mock()
            mock_method = Mock()
            mock_method.delivery_tag = "test-tag"
            mock_properties = Mock()
            mock_properties.headers = {"trace_id": "trace-123", "user_id": "user-456"}

            message_body = json.dumps(self.mock_job_data).encode("utf-8")

            # Process the message
            consumer.message_callback(mock_channel, mock_method, mock_properties, message_body)

            # Verify tracing context was used
            mock_logger.assert_called()
            logger_call = mock_logger.call_args[0][0]
            assert logger_call.trace_id == "trace-123"
            assert logger_call.job_id == "test-job-123"

    def test_message_parsing_edge_cases(self) -> None:
        """Test message parsing with various edge cases."""
        consumer = RabbitMQConsumer()

        test_cases = [
            # Missing required fields
            {"jdUrl": "https://example.com/job"},  # Missing jobId
            {"jobId": "test-123"},  # Missing jdUrl
            # Invalid data types
            {"jobId": 123, "jdUrl": "https://example.com/job"},  # jobId should be string
            # Empty values
            {"jobId": "", "jdUrl": ""},
            # Invalid JSON
            "invalid json string",
        ]

        for case in test_cases:
            mock_channel = Mock()
            mock_method = Mock()
            mock_method.delivery_tag = "test-tag"
            mock_properties = Mock()
            mock_properties.headers = {}

            if isinstance(case, str):
                message_body = case.encode("utf-8")
            else:
                message_body = json.dumps(case).encode("utf-8")

            # Should handle gracefully without crashing
            try:
                consumer.message_callback(mock_channel, mock_method, mock_properties, message_body)
                # If no exception, message should be acknowledged to avoid reprocessing
                mock_channel.basic_ack.assert_called_with(delivery_tag="test-tag")
            except Exception as e:
                # If exception occurs, it should be logged but not crash the consumer
                assert isinstance(e, (ValueError, json.JSONDecodeError))


class TestHealthEndpoint:
    """Test the health check endpoint."""

    @pytest.mark.asyncio
    async def test_health_endpoint_success(self) -> None:
        """Test health endpoint returns success when all dependencies are available."""
        with (
            patch.dict("os.environ", {"OPENAI_API_KEY": "test-key", "RABBITMQ_HOST": "localhost"}),
            patch("pika.BlockingConnection") as mock_connection,
        ):

            # Mock successful RabbitMQ connection
            mock_conn_instance = Mock()
            mock_connection.return_value = mock_conn_instance

            from fastapi.testclient import TestClient

            client = TestClient(app)

            response = client.get("/healthz")

            assert response.status_code == 200
            data = response.json()
            assert data["status"] == "ok"
            assert data["components"]["openai"] == "ok"
            assert data["components"]["rabbitmq"] == "ok"

    @pytest.mark.asyncio
    async def test_health_endpoint_degraded(self) -> None:
        """Test health endpoint returns degraded when dependencies are unavailable."""
        with (
            patch.dict("os.environ", {}, clear=True),
            patch("pika.BlockingConnection") as mock_connection,
        ):

            # Mock failed RabbitMQ connection
            mock_connection.side_effect = Exception("Connection failed")

            from fastapi.testclient import TestClient

            client = TestClient(app)

            response = client.get("/healthz")

            assert response.status_code == 200
            data = response.json()
            assert data["status"] == "degraded"
            assert "missing OPENAI_API_KEY" in data["components"]["openai"]
            assert "Connection failed" in data["components"]["rabbitmq"]


class TestMetricsEndpoint:
    """Test the metrics endpoint."""

    def test_metrics_endpoint(self) -> None:
        """Test that metrics endpoint returns Prometheus format."""
        from fastapi.testclient import TestClient

        client = TestClient(app)

        response = client.get("/metrics")

        assert response.status_code == 200
        assert response.headers["content-type"] == "text/plain; version=0.0.4; charset=utf-8"

        # Should contain our custom metrics
        content = response.text
        assert "jobs_processed_total" in content
        assert "job_processing_seconds" in content
        assert "web_scraping_total" in content


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
