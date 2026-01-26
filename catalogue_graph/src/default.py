import typing

import structlog

from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


def lambda_handler(event: dict, context: typing.Any) -> None:
    setup_logging(
        ExecutionContext(
            trace_id=get_trace_id(context),
            pipeline_step="default",
        )
    )
    logger.info("Hello from the default lambda handler")
