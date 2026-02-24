import argparse
import typing

import structlog

from clients.neptune_client import NeptuneClient
from models.events import (
    DEFAULT_INSERT_ERROR_THRESHOLD,
    BulkLoaderEvent,
    BulkLoadPollerEvent,
    EntityType,
    TransformerType,
)
from utils.argparse import add_pipeline_event_args
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


def handler(
    event: BulkLoaderEvent,
    execution_context: ExecutionContext | None = None,
) -> BulkLoadPollerEvent:
    setup_logging(execution_context)

    s3_file_uri = event.get_s3_uri()

    logger.info(
        "Starting bulk load",
        s3_file_uri=s3_file_uri,
        transformer_type=event.transformer_type,
        entity_type=event.entity_type,
    )

    neptune_client = NeptuneClient(event.environment)
    load_id = neptune_client.initiate_bulk_load(s3_file_uri=s3_file_uri)

    return BulkLoadPollerEvent(
        load_id=load_id, insert_error_threshold=event.insert_error_threshold
    )


def lambda_handler(event: dict, context: typing.Any) -> dict[str, str]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="graph_bulk_loader",
    )
    return handler(BulkLoaderEvent(**event), execution_context).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    add_pipeline_event_args(parser, {"pipeline_date", "window", "environment"})
    parser.add_argument(
        "--transformer-type",
        type=str,
        choices=typing.get_args(TransformerType),
        help="Which transformer's output to bulk load.",
        required=True,
    )
    parser.add_argument(
        "--entity-type",
        type=str,
        choices=typing.get_args(EntityType),
        help="Which entity type to bulk load (nodes or edges).",
        required=True,
    )
    parser.add_argument(
        "--insert-error-threshold",
        type=float,
        help="Maximum insert errors as a fraction of total records to still consider the bulk load successful.",
        default=DEFAULT_INSERT_ERROR_THRESHOLD,
        required=False,
    )

    args = parser.parse_args()
    event = BulkLoaderEvent.from_argparser(args)

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="graph_bulk_loader",
    )
    result = handler(
        event,
        execution_context,
    )
    logger.info("Bulk load initiated", load_id=result.load_id)


if __name__ == "__main__":
    local_handler()
