from transformers.base_transformer import EntityType
from transformers.transformer_type import TransformerType
from utils.aws import get_neptune_client

import typing
import argparse
import time

S3_BULK_LOAD_BUCKET_NAME = "wellcomecollection-neptune-graph-loader"


def handler(transformer_type: str, entity_type: EntityType, is_local=False):
    file_name = f"{transformer_type}__{entity_type}.csv"
    s3_file_uri = f"s3://{S3_BULK_LOAD_BUCKET_NAME}/{file_name}"

    print(f"Initiating bulk load from {s3_file_uri}.")

    neptune_client = get_neptune_client(is_local)
    load_id = neptune_client.initiate_bulk_load(s3_file_uri=s3_file_uri)

    while True:
        final_status = neptune_client.get_bulk_load_status(load_id)

        if final_status is not None:
            break

        time.sleep(20)

    if final_status != "LOAD_COMPLETED":
        raise Exception("Load failed. See error log above.")


def lambda_handler(event: dict, context):
    transformer_type = TransformerType.argparse(event["transformer_type"])
    entity_type = event["entity_type"]
    handler(transformer_type, entity_type)


def local_handler():
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--transformer-type",
        type=TransformerType.argparse,
        choices=list(TransformerType),
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
    args = parser.parse_args()

    handler(**args.__dict__, is_local=True)


if __name__ == "__main__":
    local_handler()
