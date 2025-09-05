import json
import unittest.mock
import uuid

import pika.spec
from main import RabbitMQConsumer


def test_message_callback_happy_path() -> None:
    # Mock the external dependencies
    with (
        unittest.mock.patch("httpx.Client") as mock_client_class,
        unittest.mock.patch("main.scrape_jd_text") as mock_scrape,
        unittest.mock.patch("main.create_cover_letter_chain") as mock_create_chain,
    ):

        # Arrange - Setup mock objects
        mock_channel = unittest.mock.Mock()
        mock_method = unittest.mock.Mock()
        mock_method.delivery_tag = "test_delivery_tag"
        mock_properties = unittest.mock.Mock(spec=pika.spec.BasicProperties)

        # Create mock client instance and post method
        mock_client_instance = unittest.mock.Mock()
        mock_client_class.return_value.__enter__.return_value = mock_client_instance
        mock_post = mock_client_instance.post

        # Setup AI chain mocks
        mock_scrape.return_value = "Mock job description text"
        mock_chain_instance = unittest.mock.Mock()
        mock_chain_instance.invoke.return_value = (
            "Mock generated cover letter content for testing purposes"
        )
        mock_create_chain.return_value = mock_chain_instance

        # Create sample message body with UUID and job URL
        test_job_id = str(uuid.uuid4())
        message_body = json.dumps(
            {"jobId": test_job_id, "jdUrl": "https://example.com/job-description"}
        ).encode("utf-8")

        # Act - Execute the function under test
        consumer = RabbitMQConsumer()
        consumer.message_callback(mock_channel, mock_method, mock_properties, message_body)

        # Assert - Verify all expected interactions
        # Verify httpx.Client.post was called exactly twice
        assert mock_post.call_count == 2

        # Verify the first call was with PROCESSING status
        first_call = mock_post.call_args_list[0]
        assert first_call[1]["json"] == {"status": "PROCESSING"}

        # Verify the second call was with COMPLETED status and content
        second_call = mock_post.call_args_list[1]
        expected_payload = {
            "status": "COMPLETED",
            "content": "Mock generated cover letter content for testing purposes",
        }
        assert second_call[1]["json"] == expected_payload

        # Verify AI chain functions were called
        mock_scrape.assert_called_once_with("https://example.com/job-description")
        mock_create_chain.assert_called_once()
        mock_chain_instance.invoke.assert_called_once_with({"jd_text": "Mock job description text"})

        # Verify channel.basic_ack was called exactly once
        mock_channel.basic_ack.assert_called_once_with(delivery_tag="test_delivery_tag")
