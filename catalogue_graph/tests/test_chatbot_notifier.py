"""Tests for the ChatbotNotifier client."""

from __future__ import annotations

import json

import pytest

from clients.chatbot_notifier import ChatbotMessage, ChatbotNotifier, ChatbotResponse
from tests.mocks import MockSNSClient


@pytest.fixture(autouse=True)
def reset_mocks() -> None:
    """Reset mock state before each test."""
    MockSNSClient.reset_mocks()


@pytest.fixture
def mock_sns_client() -> MockSNSClient:
    """Provide a mock SNS client."""
    return MockSNSClient()


@pytest.fixture
def topic_arn() -> str:
    """Provide a test topic ARN."""
    return "arn:aws:sns:eu-west-1:123456789012:test-chatbot-topic"


@pytest.fixture
def notifier(mock_sns_client: MockSNSClient, topic_arn: str) -> ChatbotNotifier:
    """Provide a ChatbotNotifier instance with mock client."""
    return ChatbotNotifier(sns_client=mock_sns_client, topic_arn=topic_arn)


def test_send_simple_message(notifier: ChatbotNotifier, topic_arn: str) -> None:
    """Test sending a simple message without threading."""
    message = ChatbotMessage(text="Hello from the pipeline!")

    response = notifier.send_notification(message)

    # Verify response
    assert isinstance(response, ChatbotResponse)
    assert response.message_id == "mock-message-id-1"

    # Verify SNS publish was called correctly
    assert len(MockSNSClient.publish_calls) == 1
    publish_call = MockSNSClient.publish_calls[0]

    assert publish_call["TopicArn"] == topic_arn
    assert publish_call["MessageStructure"] == "json"

    # Parse the nested JSON structure
    outer_message = json.loads(publish_call["Message"])
    chatbot_payload = json.loads(outer_message["default"])

    assert chatbot_payload["version"] == "1.0"
    assert chatbot_payload["source"] == "custom"
    assert chatbot_payload["content"]["textType"] == "client-markdown"
    assert chatbot_payload["content"]["description"] == "Hello from the pipeline!"

    # Optional fields should not be present when not provided
    assert "title" not in chatbot_payload["content"]
    assert "nextSteps" not in chatbot_payload["content"]
    assert "keywords" not in chatbot_payload["content"]

    # No metadata for non-threaded messages without metadata
    assert "metadata" not in chatbot_payload


def test_send_message_with_thread_id(notifier: ChatbotNotifier, topic_arn: str) -> None:
    """Test sending a message with a thread ID for conversation context."""
    message = ChatbotMessage(
        text="Follow-up message",
        thread_id="previous-message-id-123",
    )

    response = notifier.send_notification(message)

    assert isinstance(response, ChatbotResponse)
    assert response.message_id == "mock-message-id-1"

    # Verify thread ID is in the metadata
    assert len(MockSNSClient.publish_calls) == 1
    publish_call = MockSNSClient.publish_calls[0]

    outer_message = json.loads(publish_call["Message"])
    chatbot_payload = json.loads(outer_message["default"])

    assert "metadata" in chatbot_payload
    assert chatbot_payload["metadata"]["threadId"] == "previous-message-id-123"


def test_send_message_with_metadata(notifier: ChatbotNotifier, topic_arn: str) -> None:
    """Test sending a message with additional context metadata."""
    message = ChatbotMessage(
        text="Message with context",
        additional_context={
            "job_id": "202512021430",
            "adapter": "axiell",
            "status": "success",
        },
    )

    response = notifier.send_notification(message)

    assert isinstance(response, ChatbotResponse)

    # Verify metadata is included in the additionalContext
    publish_call = MockSNSClient.publish_calls[0]
    outer_message = json.loads(publish_call["Message"])
    chatbot_payload = json.loads(outer_message["default"])

    assert "metadata" in chatbot_payload
    assert "additionalContext" in chatbot_payload["metadata"]
    assert chatbot_payload["metadata"]["additionalContext"]["job_id"] == "202512021430"
    assert chatbot_payload["metadata"]["additionalContext"]["adapter"] == "axiell"
    assert chatbot_payload["metadata"]["additionalContext"]["status"] == "success"


def test_send_markdown_message(notifier: ChatbotNotifier) -> None:
    """Test sending a message with markdown formatting."""
    message = ChatbotMessage(
        text="# Alert\n\n**Status:** Failed\n\n- Item 1\n- Item 2",
        textType="client-markdown",
    )

    response = notifier.send_notification(message)

    assert isinstance(response, ChatbotResponse)

    # Verify markdown text and textType are preserved
    publish_call = MockSNSClient.publish_calls[0]
    outer_message = json.loads(publish_call["Message"])
    chatbot_payload = json.loads(outer_message["default"])

    assert chatbot_payload["content"]["textType"] == "client-markdown"
    assert "# Alert" in chatbot_payload["content"]["description"]
    assert "**Status:** Failed" in chatbot_payload["content"]["description"]


def test_threading_conversation(notifier: ChatbotNotifier) -> None:
    """Test a threaded conversation with multiple messages."""
    # Send initial message
    first_message = ChatbotMessage(text="Starting a new job")
    first_response = notifier.send_notification(first_message)

    # Send follow-up in the same thread
    second_message = ChatbotMessage(
        text="Job is progressing...",
        thread_id=first_response.message_id,
    )
    notifier.send_notification(second_message)

    # Send final message in the thread
    third_message = ChatbotMessage(
        text="Job completed successfully!",
        thread_id=first_response.message_id,
    )
    notifier.send_notification(third_message)

    # Verify all three messages were sent
    assert len(MockSNSClient.publish_calls) == 3

    # First message has no thread
    first_payload = json.loads(
        json.loads(MockSNSClient.publish_calls[0]["Message"])["default"]
    )
    assert "metadata" not in first_payload

    # Second and third messages reference the first message's ID in metadata
    second_payload = json.loads(
        json.loads(MockSNSClient.publish_calls[1]["Message"])["default"]
    )
    assert second_payload["metadata"]["threadId"] == "mock-message-id-1"

    third_payload = json.loads(
        json.loads(MockSNSClient.publish_calls[2]["Message"])["default"]
    )
    assert third_payload["metadata"]["threadId"] == "mock-message-id-1"


def test_publish_failure_raises_error(
    mock_sns_client: MockSNSClient, topic_arn: str
) -> None:
    """Test that a failed SNS publish raises a RuntimeError."""

    # Override the mock to return a failure response
    def failing_publish(**kwargs: dict) -> dict:  # type: ignore[type-arg]
        MockSNSClient.publish_calls.append(kwargs)
        return {
            "MessageId": "mock-message-id",
            "ResponseMetadata": {"HTTPStatusCode": 500},
        }

    mock_sns_client.publish = failing_publish  # type: ignore[method-assign]

    notifier = ChatbotNotifier(sns_client=mock_sns_client, topic_arn=topic_arn)
    message = ChatbotMessage(text="This should fail")

    with pytest.raises(RuntimeError, match="Failed to publish to SNS"):
        notifier.send_notification(message)


def test_publish_exception_propagates(
    mock_sns_client: MockSNSClient, topic_arn: str
) -> None:
    """Test that exceptions from the SNS client are propagated."""

    # Override the mock to raise an exception
    def exception_publish(**kwargs: dict) -> dict:  # type: ignore[type-arg]
        raise ValueError("Network error")

    mock_sns_client.publish = exception_publish  # type: ignore[method-assign]

    notifier = ChatbotNotifier(sns_client=mock_sns_client, topic_arn=topic_arn)
    message = ChatbotMessage(text="This should raise an exception")

    with pytest.raises(ValueError, match="Network error"):
        notifier.send_notification(message)


def test_send_message_with_all_fields(notifier: ChatbotNotifier) -> None:
    """Test sending a message with all optional fields populated."""
    message = ChatbotMessage(
        text="Comprehensive notification with all fields",
        thread_id="test-thread-123",
        title="Pipeline Alert",
        next_steps=[
            "Review the logs in CloudWatch",
            "Check the error metrics dashboard",
            "Contact the on-call engineer if needed",
        ],
        keywords=["pipeline", "error", "urgent"],
        additional_context={
            "environment": "production",
            "service": "catalogue-pipeline",
            "severity": "high",
        },
    )

    response = notifier.send_notification(message)
    assert isinstance(response, ChatbotResponse)

    # Verify all fields are in the payload
    publish_call = MockSNSClient.publish_calls[0]
    outer_message = json.loads(publish_call["Message"])
    chatbot_payload = json.loads(outer_message["default"])

    # Verify content fields
    assert (
        chatbot_payload["content"]["description"]
        == "Comprehensive notification with all fields"
    )
    assert chatbot_payload["content"]["title"] == "Pipeline Alert"
    assert chatbot_payload["content"]["nextSteps"] == [
        "Review the logs in CloudWatch",
        "Check the error metrics dashboard",
        "Contact the on-call engineer if needed",
    ]
    assert chatbot_payload["content"]["keywords"] == ["pipeline", "error", "urgent"]

    # Verify metadata fields
    assert chatbot_payload["metadata"]["threadId"] == "test-thread-123"
    assert (
        chatbot_payload["metadata"]["additionalContext"]["environment"] == "production"
    )
    assert (
        chatbot_payload["metadata"]["additionalContext"]["service"]
        == "catalogue-pipeline"
    )
    assert chatbot_payload["metadata"]["additionalContext"]["severity"] == "high"
