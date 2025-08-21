import argparse
import typing

from models.events import (
    DEFAULT_INSERT_ERROR_THRESHOLD,
    BulkLoaderEvent,
    EntityType,
    TransformerType,
)
from utils.aws import get_neptune_client


def handler(event: BulkLoaderEvent, is_local: bool = False) -> dict[str, typing.Any]:
    s3_file_uri = event.get_bulk_load_s3_uri()
    print(f"Initiating bulk load from {s3_file_uri}.")

    neptune_client = get_neptune_client(is_local)
    load_id = neptune_client.initiate_bulk_load(s3_file_uri=s3_file_uri)

    return {"load_id": load_id, "insert_error_threshold": event.insert_error_threshold}


def lambda_handler(event: dict, context: typing.Any) -> dict[str, str]:
    return handler(BulkLoaderEvent(**event))


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

    print(handler(event, is_local=True))


if __name__ == "__main__":
    local_handler()
