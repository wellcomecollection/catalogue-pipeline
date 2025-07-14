import argparse
import datetime
import json
import typing

import config
import smart_open
from bulk_loader import DEFAULT_INSERT_ERROR_THRESHOLD
from utils.aws import get_neptune_client
from utils.slack import publish_report


def log_payload(payload: dict) -> None:
    """Log the bulk load result into a JSON file which stores all results from the latest pipeline run"""
    # Extract the name of the bulk load file to use as a key in the JSON log.
    bulk_load_file_uri = payload["overallStatus"]["fullUri"]
    bulk_load_file_name = bulk_load_file_uri.split("/")[-1].split(".")[0]
    log_file_uri = (
        f"s3://{config.S3_BULK_LOAD_BUCKET_NAME}/report.neptune_bulk_loader.json"
    )

    try:
        with smart_open.open(log_file_uri, "r") as f:
            bulk_load_log = json.loads(f.read())
    except (OSError, KeyError):
        # On the first run, the log file might not exist
        bulk_load_log = {}

    # Overwrite the existing result (from the last bulk load) with the current one
    bulk_load_log[bulk_load_file_name] = payload

    # Save the log file back to S3
    with smart_open.open(log_file_uri, "w") as f:
        f.write(json.dumps(bulk_load_log, indent=2))


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
    load_id: str, insert_error_threshold: float, is_local: bool = False
) -> dict[str, str]:
    # Response format: https://docs.aws.amazon.com/neptune/latest/userguide/load-api-reference-status-response.html
    payload = get_neptune_client(is_local).get_bulk_load_status(load_id)
    overall_status = payload["overallStatus"]

    # Statuses: https://docs.aws.amazon.com/neptune/latest/userguide/loader-message.html
    status: str = overall_status["status"]
    processed_count = overall_status["totalRecords"]

    print(f"Bulk load status: {status}. (Processed {processed_count:,} records.)")

    if status in ("LOAD_NOT_STARTED", "LOAD_IN_QUEUE", "LOAD_IN_PROGRESS"):
        return {
            "loadId": load_id,
            "status": "IN_PROGRESS",
        }

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
        and (insert_error_count / processed_count < insert_error_threshold)
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
        return {
            "loadId": load_id,
            "status": "SUCCEEDED",
        }

    raise Exception("Load failed. See error log above.")


def lambda_handler(event: dict, context: typing.Any) -> dict[str, str]:
    load_id = event["load_id"]
    insert_error_threshold = event.get(
        "insert_error_threshold", DEFAULT_INSERT_ERROR_THRESHOLD
    )
    return handler(load_id, insert_error_threshold)


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

    handler(**args.__dict__, is_local=True)


if __name__ == "__main__":
    local_handler()
