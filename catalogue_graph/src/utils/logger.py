"""
Contextual logging implementation using structlog.
"""

import os
import sys
from datetime import UTC, datetime
from typing import Any

import structlog
from structlog.types import FilteringBoundLogger


def setup_structlog(
    log_level: str | None = None,
) -> None:
    """
    Configure structlog for structured contextual logging.

    Args:
        log_level: Log level (DEBUG, INFO, WARNING, ERROR, CRITICAL)
    """
    log_level = log_level or os.environ.get(
        "LOG_LEVEL", "WARNING"
    )  # we can adjust the log level without code change

    # Configure processors
    processors = [
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

    # Configure standard library logging level
    import logging

    logging.basicConfig(
        format="%(message)s",
        level=getattr(logging, log_level.upper()),
    )


def _get_renderer():
    """Get appropriate renderer based on environment."""

    if hasattr(sys.stderr, "isatty") and sys.stderr.isatty():
        # Colored console output for local development
        return structlog.dev.ConsoleRenderer(colors=True)
    else:
        # JSON output for production/containers
        return structlog.processors.JSONRenderer()


def bind_execution_context(
    execution_id: str, execution_context: dict[str, Any] | None = None
) -> None:
    """
    Bind execution context globally for all subsequent log calls.

    Args:
        execution_id: Unique execution identifier
        execution_context: Additional context metadata
    """
    structlog.contextvars.bind_contextvars(
        execution_id=execution_id, execution_context=execution_context
    )


def get_logger(name: str | None = None) -> FilteringBoundLogger:
    """
    Get a structlog logger with automatic context from contextvars.

    Args:
        name: Optional logger name (defaults to calling module)

    Returns:
        Logger that automatically includes bound context
    """
    return structlog.get_logger(name)


def setup_logging(execution_id: str, is_local: bool = False) -> FilteringBoundLogger:
    """
    Set up structlog with execution context.

    Args:
        execution_id: Unique execution identifier provided by caller
        is_local: Whether this is running in local development mode

    Returns:
        Logger with execution context bound
    """
    # Set default log level based on environment
    setup_structlog(log_level="INFO" if is_local else "WARNING")

    # Simple execution context with just timestamp
    execution_context: dict[str, Any] = {
        "started_at": datetime.now(UTC).isoformat(),
        ## something else that is interesting
    }

    bind_execution_context(execution_id, execution_context)

    return get_logger()
