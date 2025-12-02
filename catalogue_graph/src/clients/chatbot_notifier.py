"""Client for sending custom notifications to AWS Chatbot via SNS.

This client enables sending formatted messages to AWS Chatbot channels,
with support for threading to maintain conversation context.

See: https://docs.aws.amazon.com/chatbot/latest/adminguide/custom-notifs.html
"""

from __future__ import annotations

import json
from typing import Any

from pydantic import BaseModel, Field


class ChatbotMessage(BaseModel):
    """Message to be sent to AWS Chatbot.

    Note: Only basic markdown is supported. Complex markdown features like tables,
    footnotes, and some formatting may not render correctly. See the Slack markdown
    guide for supported features: https://www.markdownguide.org/tools/slack/

    Attributes:
        text: The message content (description field). Supports markdown
            formatting. Maximum length is 8,000 characters.
        thread_id: Optional thread identifier for maintaining conversation
            context in Slack and Microsoft Teams. Custom notifications with
            the same threadId are grouped together.
        title: Optional title for the notification. Supports markdown content.
            Maximum length is 250 characters.
        next_steps: Optional next step recommendations. Rendered as a bulleted
            list and supports markdown. Each step has max length of 350 chars.
        keywords: Optional event tags and categories. Rendered as inline tags.
            Each keyword has max length of 75 characters.
        summary: Optional summary displayed on the top-level message when
            notifications are threaded.
        related_resources: Optional array of strings describing resources
            related to the custom notification.
        additional_context: Optional string-to-string dictionary for additional
            information about the notification.
        enable_custom_actions: If set to False, custom action buttons aren't
            added to the notification. Defaults to True.
        textType: Format of the text field. Only "client-markdown" is currently
            supported (default).
    """

    text: str = Field(..., max_length=8000, description="Message description")
    thread_id: str | None = None
    title: str | None = Field(None, max_length=250, description="Notification title")
    next_steps: list[str] | None = Field(
        None, description="Next step recommendations (max 350 chars each)"
    )
    keywords: list[str] | None = Field(
        None, description="Event tags/categories (max 75 chars each)"
    )
    summary: str | None = Field(
        None, description="Summary for top-level message when threaded"
    )
    related_resources: list[str] | None = Field(
        None, description="Resources related to this notification"
    )
    additional_context: dict[str, str] | None = Field(
        None, description="Additional context as string-to-string dictionary"
    )
    enable_custom_actions: bool = Field(
        False, description="Whether to add custom action buttons"
    )
    textType: str = Field(
        default="client-markdown", description="Text format type (client-markdown only)"
    )


class ChatbotResponse(BaseModel):
    """Response from sending a message to AWS Chatbot.

    Attributes:
        message_id: The SNS message ID, which can be used as a thread_id
            for subsequent messages to maintain conversation context.
    """

    message_id: str


class ChatbotNotifier:
    """Client for sending custom notifications to AWS Chatbot via SNS.

    This client wraps the SNS publish API to send formatted messages to
    AWS Chatbot channels. It handles the required message structure and
    supports threading for conversation context.

    Example:
        >>> import boto3
        >>> sns_client = boto3.client("sns")
        >>> notifier = ChatbotNotifier(
        ...     sns_client=sns_client,
        ...     topic_arn="arn:aws:sns:region:account:topic-name"
        ... )
        >>> message = ChatbotMessage(
        ...     text="# Important Alert\\nSomething happened!",
        ... )
        >>> response = notifier.send_notification(message)
        >>> # Reply in the same thread
        >>> follow_up = ChatbotMessage(
        ...     text="Update: issue resolved",
        ...     thread_id=response.message_id
        ... )
        >>> notifier.send_notification(follow_up)
    """

    def __init__(self, sns_client: Any, topic_arn: str) -> None:
        """Initialize the ChatbotNotifier.

        Args:
            sns_client: Boto3 SNS client for publishing messages.
            topic_arn: The ARN of the SNS topic configured with AWS Chatbot.
        """
        self.sns_client = sns_client
        self.topic_arn = topic_arn

    def send_notification(self, message: ChatbotMessage) -> ChatbotResponse:
        """Send a notification to AWS Chatbot.

        Args:
            message: The message to send, including optional thread context.

        Returns:
            ChatbotResponse containing the message ID for threading.

        Raises:
            RuntimeError: If the SNS publish request fails.
        """
        # Build the message payload for AWS Chatbot
        chatbot_payload: dict[str, Any] = {
            "version": "1.0",
            "source": "custom",
            "content": {
                "textType": message.textType,
                "description": message.text,
            },
        }

        # Add optional content fields
        if message.title:
            chatbot_payload["content"]["title"] = message.title
        
        if message.next_steps:
            chatbot_payload["content"]["nextSteps"] = message.next_steps
        
        if message.keywords:
            chatbot_payload["content"]["keywords"] = message.keywords

        # Build metadata section
        metadata_dict: dict[str, Any] = {}
        
        # Add thread ID to metadata if provided
        if message.thread_id:
            metadata_dict["threadId"] = message.thread_id
        
        # Add optional metadata fields
        if message.summary:
            metadata_dict["summary"] = message.summary
        
        if message.related_resources:
            metadata_dict["relatedResources"] = message.related_resources
        
        # Add additional context to metadata if provided
        if message.additional_context:
            metadata_dict["additionalContext"] = message.additional_context
        
        # Add enableCustomActions
        metadata_dict["enableCustomActions"] = message.enable_custom_actions


        
        # Add metadata to payload if any metadata exists
        if metadata_dict:
            chatbot_payload["metadata"] = metadata_dict

        # Publish to SNS with the nested JSON structure required by AWS
        response = self.sns_client.publish(
            TopicArn=self.topic_arn,
            Message=json.dumps({"default": json.dumps(chatbot_payload)}),
            MessageStructure="json",
        )

        # Check for successful publish
        if response["ResponseMetadata"]["HTTPStatusCode"] != 200:
            raise RuntimeError(f"Failed to publish to SNS: {response!r}")

        return ChatbotResponse(message_id=response["MessageId"])
