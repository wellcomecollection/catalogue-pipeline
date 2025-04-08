import argparse
import datetime
import typing

from utils.aws import get_neptune_client

INSERT_ERROR_THRESHOLD = 1/10000
    

def handler(load_id: str, is_local: bool = False) -> dict[str, str]:
    neptune_client = get_neptune_client(is_local)

    # Response format: https://docs.aws.amazon.com/neptune/latest/userguide/load-api-reference-status-response.html
    payload = neptune_client.get_bulk_load_status(load_id)
    overall_status = payload["overallStatus"]
    error_logs = payload["errors"]["errorLogs"]

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

    if error_logs:
        print("    First 10 errors:")

        for error_log in error_logs:
            code = error_log["errorCode"]
            message = error_log["errorMessage"]
            record_num = error_log["recordNum"]
            print(f"         {code}: {message}. (Row number: {record_num})")

    failed_feeds = payload.get("failedFeeds")
    if failed_feeds:
        print("    Failed feed statuses:")
        for failed_feed in failed_feeds:
            print(f"         {failed_feed['status']}")

    failed_below_insert_error_threshold = (status == "LOAD_FAILED" and parsing_error_count == 0 and data_type_error_count == 0 and (insert_error_count / processed_count < INSERT_ERROR_THRESHOLD))
    
    if failed_below_insert_error_threshold:
        print("Bulk load failed due to a very small number of insert errors. Marking as successful.")
        
    if status == "LOAD_COMPLETED" or failed_below_insert_error_threshold:
        return {
            "loadId": load_id,
            "status": "SUCCEEDED",
        }
    
    raise Exception("Load failed. See error log above.")

def lambda_handler(event: dict, context: typing.Any) -> dict[str, str]:
    load_id = event["loadId"]
    return handler(load_id)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--load-id",
        type=str,
        help="The ID of the bulk load job whose status to check.",
        required=True,
    )
    args = parser.parse_args()

    handler(**args.__dict__, is_local=True)


if __name__ == "__main__":
    local_handler()
