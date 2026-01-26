"""
Contextual logging implementation using structlog.
"""

import logging
import os
import sys
import uuid
from datetime import UTC, datetime
from typing import Any

import structlog
from pydantic import BaseModel


class ExecutionContext(BaseModel):
    trace_id: str
    pipeline_step: str


def get_trace_id(context: Any | None = None) -> str:
    """
    Get a trace ID from the Lambda context or generate a UUID.

    Args:
        context: Lambda context object (may have aws_request_id attribute)

    Returns:
        The AWS request ID if available, otherwise a generated UUID
    """
    if context is not None and hasattr(context, "aws_request_id"):
        return str(context.aws_request_id)
    return str(uuid.uuid4())


log_level = os.environ.get(
    "LOG_LEVEL", "INFO"
)  # we can adjust the log level in tf. Defaults to INFO in local


def setup_structlog() -> None:
    """
    Configure structlog for structured contextual logging.
    """

    # Configure processors
    processors: list[structlog.types.Processor] = [
        # Add log level to event dict
        structlog.stdlib.add_log_level,
        # Add logger name
        structlog.stdlib.add_logger_name,
        # Add timestamp
        structlog.processors.TimeStamper(fmt="iso"),
        # Merge in bound contextvars (for execution context)
        structlog.contextvars.merge_contextvars,
        # Filter out None values
        lambda _, __, event_dict: {
            k: v for k, v in event_dict.items() if v is not None
        },
        # Choose renderer based on environment
        _get_renderer(),
    ]

    # Filter out None processors
    processors = [p for p in processors if p is not None]

    # Configure structlog
    structlog.configure(
        processors=processors,
        wrapper_class=structlog.stdlib.BoundLogger,
        logger_factory=structlog.stdlib.LoggerFactory(),
        cache_logger_on_first_use=True,
    )

    # Configure standard library logging to output to stdout
    logging.basicConfig(
        format="%(message)s",
        level=getattr(logging, log_level.upper()),
        stream=sys.stdout,
    )


def _get_renderer() -> structlog.types.Processor:
    """Get appropriate renderer based on environment."""

    if hasattr(sys.stderr, "isatty") and sys.stderr.isatty():
        # Colored console output for local development
        return structlog.dev.ConsoleRenderer(colors=True)
    else:
        # JSON output for production/containers
        return structlog.processors.JSONRenderer()


def bind_execution_context(context: ExecutionContext) -> None:
    """
    Bind execution context globally for all subsequent log calls.

    Args:
        context: Additional context metadata
    """
    structlog.contextvars.bind_contextvars(
        trace_id=context.trace_id,
        pipeline_step=context.pipeline_step,
        started_at=datetime.now(UTC).isoformat(),
    )


def setup_logging(context: ExecutionContext) -> None:
    """
    Set up structlog with execution context.
    Args:
        context: Execution context to bind
    """
    setup_structlog()
    # Force the root logger to desired level to override any AWS Lambda defaults
    logging.getLogger().setLevel(log_level)

    bind_execution_context(context)
