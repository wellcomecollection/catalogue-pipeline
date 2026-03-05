import argparse
import datetime
import re
import typing
from typing import Literal, cast

import structlog

from clients.neptune_client import NeptuneClient
from models.events import (
    DEFAULT_INSERT_ERROR_THRESHOLD,
    BulkLoaderEvent,
    BulkLoadPollerEvent,
)
from models.incremental_window import IncrementalWindow
from models.neptune_bulk_loader import BulkLoadStatusResponse
from utils.argparse import add_pipeline_event_args
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.reporting import BulkLoaderReport
from utils.types import EntityType, Environment, TransformerType

logger = structlog.get_logger(__name__)

BulkLoadStatus = Literal["IN_PROGRESS", "SUCCEEDED"]


class BulkLoadPollerResponse(BulkLoadPollerEvent):
    status: BulkLoadStatus

    @classmethod
    def from_event(
        cls, event: BulkLoadPollerEvent, status: BulkLoadStatus
    ) -> "BulkLoadPollerResponse":
        return BulkLoadPollerResponse(**event.model_dump(), status=status)


def print_detailed_bulk_load_errors(payload: BulkLoadStatusResponse) -> None:
    error_logs = payload.errors.error_logs
    failed_feeds = payload.failed_feeds

    if error_logs:
        logger.info("First 10 errors from bulk load")

    for error_log in error_logs:
        logger.warning(
            "Bulk load error",
            error_code=error_log.error_code,
            error_message=error_log.error_message,
            record_num=error_log.record_num,
        )

    if failed_feeds:
        logger.info("Failed feed statuses")
        for failed_feed in failed_feeds:
            logger.warning("Failed feed", status=failed_feed.status)


def bulk_loader_event_from_s3_uri(
    s3_uri: str, environment: Environment
) -> BulkLoaderEvent:
    """Given a bulk load file S3 URI, reconstruct the corresponding bulk loader event."""
    regex = re.compile(
        r"^(?:s3://[^/]+/[^/]+/)"
        r"(?P<pipeline_date>[^/]+)/"
        r"(windows/(?P<window>[^/]+)/)?"
        r"(?P<transformer_type>[^/]+)__(?P<entity_type>[^/]+)\.csv$"
    )

    m = regex.match(s3_uri)
    if not m:
        raise ValueError(f"S3 uri '{s3_uri}' does not match the expected format.")

    window = None
    if raw_window := m.group("window"):
        window = IncrementalWindow.from_formatted_string(raw_window)

    return BulkLoaderEvent(
        environment=environment,
        pipeline_date=m.group("pipeline_date"),
        transformer_type=cast(TransformerType, m.group("transformer_type")),
        entity_type=cast(EntityType, m.group("entity_type")),
        window=window,
    )


def handler(
    event: BulkLoadPollerEvent,
    execution_context: ExecutionContext | None = None,
) -> BulkLoadPollerResponse:
    setup_logging(execution_context)

    neptune_client = NeptuneClient(event.environment)
    payload = neptune_client.get_bulk_load_status(event.load_id)
    overall_status = payload.overall_status

    # Statuses: https://docs.aws.amazon.com/neptune/latest/userguide/loader-message.html
    status: str = overall_status.status
    processed_count = overall_status.total_records

    logger.info(
        "Bulk load status",
        status=status,
        processed_count=processed_count,
    )

    if status in ("LOAD_NOT_STARTED", "LOAD_IN_QUEUE", "LOAD_IN_PROGRESS"):
        return BulkLoadPollerResponse.from_event(event, "IN_PROGRESS")

    insert_error_count = overall_status.insert_errors
    parsing_error_count = overall_status.parsing_errors
    data_type_error_count = overall_status.datatype_mismatch_errors
    formatted_time = datetime.timedelta(seconds=overall_status.total_time_spent)

    logger.info(
        "Bulk load completed",
        insert_errors=insert_error_count,
        parsing_errors=parsing_error_count,
        data_type_mismatch_errors=data_type_error_count,
        total_time_spent=str(formatted_time),
    )

    print_detailed_bulk_load_errors(payload)

    failed_below_insert_error_threshold = (
        status == "LOAD_FAILED"
        and parsing_error_count == 0
        and data_type_error_count == 0
        and (insert_error_count / processed_count <= event.insert_error_threshold)
    )

    bulk_loader_event = bulk_loader_event_from_s3_uri(
        overall_status.full_uri, event.environment
    )
    report = BulkLoaderReport(**bulk_loader_event.model_dump(), status=payload)
    report.publish()

    if status == "LOAD_COMPLETED" or failed_below_insert_error_threshold:
        return BulkLoadPollerResponse.from_event(event, "SUCCEEDED")

    raise Exception("Load failed. See error log above.")


def lambda_handler(event: dict, context: typing.Any) -> dict[str, typing.Any]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="graph_bulk_load_poller",
    )
    return handler(BulkLoadPollerEvent(**event), execution_context).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    add_pipeline_event_args(parser, {"environment"})
    parser.add_argument(
        "--load-id",
        type=str,
        help="The ID of the bulk load job whose status to check.",
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
    event = BulkLoadPollerEvent(**args.__dict__)

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="graph_bulk_load_poller",
    )
    handler(
        event,
        execution_context,
    )


if __name__ == "__main__":
    local_handler()
