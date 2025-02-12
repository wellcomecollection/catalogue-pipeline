import argparse
import typing

from utils.aws import get_neptune_client


def handler(load_id: str, is_local: bool = False) -> dict[str, str]:
    neptune_client = get_neptune_client(is_local)
    status = neptune_client.get_bulk_load_status(load_id)

    if status in ("LOAD_NOT_STARTED", "LOAD_IN_QUEUE", "LOAD_IN_PROGRESS"):
        return {
            "loadId": load_id,
            "status": "IN_PROGRESS",
        }

    if status == "LOAD_COMPLETED":
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
