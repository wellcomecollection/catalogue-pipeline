import argparse
import typing

import structlog

from models.events import (
    DEFAULT_INSERT_ERROR_THRESHOLD,
    BulkLoaderEvent,
    BulkLoadPollerEvent,
    EntityType,
    TransformerType,
)
from utils.aws import get_neptune_client
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


def handler(
    event: BulkLoaderEvent,
    execution_context: ExecutionContext,
    is_local: bool = False,
) -> BulkLoadPollerEvent:
    setup_logging(execution_context)

    s3_file_uri = event.get_s3_uri()

    logger.info(
        "Starting bulk load",
        s3_file_uri=s3_file_uri,
        transformer_type=event.transformer_type,
        entity_type=event.entity_type,
    )

    neptune_client = get_neptune_client(is_local)
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
        "--pipeline-date",
        type=str,
        help="The pipeline date associated with the loaded items.",
        default="dev",
        required=False,
    )
    parser.add_argument(
        "--window-start",
        type=str,
        help="Start of the processed window (e.g. 2025-01-01T00:00). Incremental mode only.",
        required=False,
    )
    parser.add_argument(
        "--window-end",
        type=str,
        help="End of the processed window (e.g. 2025-01-01T00:00). Incremental mode only.",
        required=False,
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
    result = handler(event, execution_context, is_local=True)
    logger.info("Bulk load initiated", load_id=result.load_id)


if __name__ == "__main__":
    local_handler()
