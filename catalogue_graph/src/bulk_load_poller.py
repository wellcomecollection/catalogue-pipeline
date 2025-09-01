import argparse
import datetime
import json
import typing
from pathlib import Path
from typing import Literal
from urllib.parse import urlparse

import config
import smart_open
from models.events import DEFAULT_INSERT_ERROR_THRESHOLD, BulkLoadPollerEvent
from utils.aws import get_neptune_client
from utils.slack import publish_report

BulkLoadStatus = Literal["IN_PROGRESS", "SUCCEEDED"]


class BulkLoadPollerResponse(BulkLoadPollerEvent):
    status: BulkLoadStatus

    @classmethod
    def from_event(
        cls, event: BulkLoadPollerEvent, status: BulkLoadStatus
    ) -> "BulkLoadPollerResponse":
        return BulkLoadPollerResponse(**event.model_dump(), status=status)


def log_payload(payload: dict) -> None:
    """Log the bulk load result into a JSON file stored in the same location as the corresponding bulk load file."""
    bulk_load_file_uri = payload["overallStatus"]["fullUri"]

    parsed_uri = urlparse(bulk_load_file_uri)
    path = Path(parsed_uri.path)
    log_file_uri = f"s3://{parsed_uri.netloc}{path.parent}/report.{path.stem}.json"
    with smart_open.open(log_file_uri, "w") as f:
        f.write(json.dumps(payload, indent=2))


def print_detailed_bulk_load_errors(payload: dict) -> None:
    error_logs = payload["errors"]["errorLogs"]
    failed_feeds = payload.get("failedFeeds")

    if error_logs:
        print("    First 10 errors:")

    for error_log in error_logs:
        code = error_log["errorCode"]
        message = error_log["errorMessage"]
        record_num = error_log["recordNum"]
        print(f"         {code}: {message}. (Row number: {record_num})")

    if failed_feeds:
        print("    Failed feed statuses:")
        for failed_feed in failed_feeds:
            print(f"         {failed_feed['status']}")


def handler(
    event: BulkLoadPollerEvent, is_local: bool = False
) -> BulkLoadPollerResponse:
    # Response format: https://docs.aws.amazon.com/neptune/latest/userguide/load-api-reference-status-response.html
    payload = get_neptune_client(is_local).get_bulk_load_status(event.load_id)
    overall_status = payload["overallStatus"]

    # Statuses: https://docs.aws.amazon.com/neptune/latest/userguide/loader-message.html
    status: str = overall_status["status"]
    processed_count = overall_status["totalRecords"]

    print(f"Bulk load status: {status}. (Processed {processed_count:,} records.)")

    if status in ("LOAD_NOT_STARTED", "LOAD_IN_QUEUE", "LOAD_IN_PROGRESS"):
        return BulkLoadPollerResponse.from_event(event, "IN_PROGRESS")

    insert_error_count = overall_status["insertErrors"]
    parsing_error_count = overall_status["parsingErrors"]
    data_type_error_count = overall_status["datatypeMismatchErrors"]
    formatted_time = datetime.timedelta(seconds=overall_status["totalTimeSpent"])

    print(f"    Insert errors: {insert_error_count:,}")
    print(f"    Parsing errors: {parsing_error_count:,}")
    print(f"    Data type mismatch errors: {data_type_error_count:,}")
    print(f"    Total time spent: {formatted_time}")

    print_detailed_bulk_load_errors(payload)

    failed_below_insert_error_threshold = (
        status == "LOAD_FAILED"
        and parsing_error_count == 0
        and data_type_error_count == 0
        and (insert_error_count / processed_count < event.insert_error_threshold)
    )

    if failed_below_insert_error_threshold:
        print(
            "Bulk load failed due to a very small number of insert errors. Marking as successful."
        )
        if not is_local:
            bulk_load_type = overall_status["fullUri"].split("/")[-1].split(".")[0]
            report = [
                {
                    "type": "header",
                    "text": {
                        "type": "plain_text",
                        "emoji": True,
                        "text": ":warning: Concepts loader :spider_web:",
                    },
                },
                {
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": "\n".join(
                            [
                                f"Loading *{bulk_load_type}* into the graph resulted in *{insert_error_count} insert errors*.",
                                f"Total time spent: {formatted_time}.",
                            ]
                        ),
                    },
                },
            ]
            publish_report(report, slack_secret=config.SLACK_SECRET_ID)

    if not is_local:
        log_payload(payload)

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
