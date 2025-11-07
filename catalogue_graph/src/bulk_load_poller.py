import argparse
import datetime
import re
import typing
from typing import Literal, cast

from models.events import (
    DEFAULT_INSERT_ERROR_THRESHOLD,
    BulkLoaderEvent,
    BulkLoadPollerEvent,
)
from models.incremental_window import IncrementalWindow
from models.neptune_bulk_loader import BulkLoadStatusResponse
from utils.aws import get_neptune_client
from utils.reporting import BulkLoaderReport
from utils.types import EntityType, TransformerType

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
        print("    First 10 errors:")

    for error_log in error_logs:
        code = error_log.error_code
        message = error_log.error_message
        record_num = error_log.record_num
        print(f"         {code}: {message}. (Row number: {record_num})")

    if failed_feeds:
        print("    Failed feed statuses:")
        for failed_feed in failed_feeds:
            print(f"         {failed_feed.status}")


def bulk_loader_event_from_s3_uri(s3_uri: str) -> BulkLoaderEvent:
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
        pipeline_date=m.group("pipeline_date"),
        transformer_type=cast(TransformerType, m.group("transformer_type")),
        entity_type=cast(EntityType, m.group("entity_type")),
        window=window,
    )


def handler(
    event: BulkLoadPollerEvent, is_local: bool = False
) -> BulkLoadPollerResponse:
    payload = get_neptune_client(is_local).get_bulk_load_status(event.load_id)
    overall_status = payload.overall_status

    # Statuses: https://docs.aws.amazon.com/neptune/latest/userguide/loader-message.html
    status: str = overall_status.status
    processed_count = overall_status.total_records

    print(f"Bulk load status: {status}. (Processed {processed_count:,} records.)")

    if status in ("LOAD_NOT_STARTED", "LOAD_IN_QUEUE", "LOAD_IN_PROGRESS"):
        return BulkLoadPollerResponse.from_event(event, "IN_PROGRESS")

    insert_error_count = overall_status.insert_errors
    parsing_error_count = overall_status.parsing_errors
    data_type_error_count = overall_status.datatype_mismatch_errors
    formatted_time = datetime.timedelta(seconds=overall_status.total_time_spent)

    print(f"    Insert errors: {insert_error_count:,}")
    print(f"    Parsing errors: {parsing_error_count:,}")
    print(f"    Data type mismatch errors: {data_type_error_count:,}")
    print(f"    Total time spent: {formatted_time}")

    print_detailed_bulk_load_errors(payload)

    failed_below_insert_error_threshold = (
        status == "LOAD_FAILED"
        and parsing_error_count == 0
        and data_type_error_count == 0
        and (insert_error_count / processed_count <= event.insert_error_threshold)
    )

    if not is_local:
        bulk_loader_event = bulk_loader_event_from_s3_uri(overall_status.full_uri)
        report = BulkLoaderReport(**bulk_loader_event.model_dump(), status=payload)
        report.publish()

    if status == "LOAD_COMPLETED" or failed_below_insert_error_threshold:
        return BulkLoadPollerResponse.from_event(event, "SUCCEEDED")

    raise Exception("Load failed. See error log above.")


def lambda_handler(event: dict, context: typing.Any) -> dict[str, typing.Any]:
    return handler(BulkLoadPollerEvent(**event)).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
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

    handler(event, is_local=True)


if __name__ == "__main__":
    local_handler()
