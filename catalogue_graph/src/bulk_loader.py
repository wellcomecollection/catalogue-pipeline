import argparse
import typing

import config
from transformers.base_transformer import EntityType
from transformers.create_transformer import TransformerType
from utils.aws import get_neptune_client

DEFAULT_INSERT_ERROR_THRESHOLD = 1 / 10000


def handler(
    transformer_type: TransformerType,
    entity_type: EntityType,
    insert_error_threshold: int,
    is_local: bool = False,
) -> dict[str, str]:
    file_name = f"{transformer_type}__{entity_type}.csv"
    s3_file_uri = f"s3://{config.S3_BULK_LOAD_BUCKET_NAME}/{file_name}"

    print(f"Initiating bulk load from {s3_file_uri}.")

    neptune_client = get_neptune_client(is_local)
    load_id = neptune_client.initiate_bulk_load(s3_file_uri=s3_file_uri)

    return {"load_id": load_id, "insert_error_threshold": insert_error_threshold}


def lambda_handler(event: dict, context: typing.Any) -> dict[str, str]:
    transformer_type = event["transformer_type"]
    entity_type = event["entity_type"]
    insert_error_threshold = event.get(
        "insert_error_threshold", DEFAULT_INSERT_ERROR_THRESHOLD
    )
    return handler(transformer_type, entity_type, insert_error_threshold)


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
        "--insert-error-threshold",
        type=int,
        help="Maximum insert errors as a fraction of total records to still consider the bulk load successful.",
        default=DEFAULT_INSERT_ERROR_THRESHOLD,
        required=False,
    )
    args = parser.parse_args()

    print(handler(**args.__dict__, is_local=True))


if __name__ == "__main__":
    local_handler()
