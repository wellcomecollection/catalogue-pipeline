import argparse
import os
import typing

from transformers.base_transformer import BaseTransformer, EntityType, StreamDestination
from transformers.create_transformer import TransformerType, create_transformer
from utils.aws import get_neptune_client

CHUNK_SIZE = 256
S3_BULK_LOAD_BUCKET_NAME = os.environ["S3_BULK_LOAD_BUCKET_NAME"]
GRAPH_QUERIES_SNS_TOPIC_ARN = os.environ["GRAPH_QUERIES_SNS_TOPIC_ARN"]


def handler(
    stream_destination: StreamDestination,
    transformer_type: TransformerType,
    entity_type: EntityType,
    sample_size: int | None = None,
    is_local: bool = False,
) -> None:
    print(
        f"Transforming {sample_size or 'all'} {entity_type} using the {transformer_type} "
        f"transformer and streaming them into {stream_destination}."
    )

    transformer: BaseTransformer = create_transformer(transformer_type)

    if stream_destination == "graph":
        neptune_client = get_neptune_client(is_local)
        transformer.stream_to_graph(
            neptune_client, entity_type, CHUNK_SIZE, sample_size
        )
    elif stream_destination == "s3":
        file_name = f"{transformer_type}__{entity_type}.csv"
        s3_uri = f"s3://{S3_BULK_LOAD_BUCKET_NAME}/{file_name}"
        transformer.stream_to_s3(s3_uri, entity_type, CHUNK_SIZE, sample_size)
    elif stream_destination == "sns":
        transformer.stream_to_sns(
            GRAPH_QUERIES_SNS_TOPIC_ARN, entity_type, CHUNK_SIZE, sample_size
        )
    else:
        raise ValueError("Unsupported stream destination.")


def lambda_handler(event: dict, context: typing.Any) -> None:
    stream_destination = event["stream_destination"]
    transformer_type = event["transformer_type"]
    entity_type = event["entity_type"]
    sample_size = event.get("sample_size")

    handler(stream_destination, transformer_type, entity_type, sample_size)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--transformer-type",
        type=str,
        choices=typing.get_args(TransformerType),
        help="Which transformer to use for streaming.",
        required=True,
    )
    parser.add_argument(
        "--entity-type",
        type=str,
        choices=typing.get_args(EntityType),
        help="Which entity type to transform using the specified transformer (nodes or edges).",
        required=True,
    )
    parser.add_argument(
        "--stream-destination",
        type=str,
        choices=typing.get_args(StreamDestination),
        help="Where to stream the transformed entities.",
        required=True,
    )
    parser.add_argument(
        "--sample-size",
        type=int,
        help="How many entities to stream. If not specified, streaming will continue until the source is exhausted.",
    )
    args = parser.parse_args()

    handler(**args.__dict__, is_local=True)


if __name__ == "__main__":
    local_handler()
