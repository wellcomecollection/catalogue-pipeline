#!/usr/bin/env python

import argparse
import typing

import config
from transformers.base_transformer import BaseTransformer, EntityType, StreamDestination
from transformers.create_transformer import TransformerType, create_transformer
from utils.aws import get_neptune_client


class LambdaEvent(typing.TypedDict):
    transformer_type: TransformerType
    entity_type: EntityType
    stream_destination: StreamDestination
    pipeline_date: str | None
    sample_size: int | None


def handler(
    stream_destination: StreamDestination,
    transformer_type: TransformerType,
    entity_type: EntityType,
    pipeline_date: str | None = None,
    sample_size: int | None = None,
    is_local: bool = False,
) -> None:
    print(
        f"Transforming {sample_size or 'all'} {entity_type} using the {transformer_type} "
        f"transformer and streaming them into {stream_destination}."
    )

    transformer: BaseTransformer = create_transformer(
        transformer_type, entity_type, pipeline_date, is_local
    )

    if stream_destination == "graph":
        neptune_client = get_neptune_client(is_local)
        transformer.stream_to_graph(neptune_client, entity_type, sample_size)
    elif stream_destination == "s3":
        file_name = f"{transformer_type}__{entity_type}.csv"
        s3_uri = f"s3://{config.S3_BULK_LOAD_BUCKET_NAME}/{file_name}"
        transformer.stream_to_s3(s3_uri, entity_type, sample_size)
    elif stream_destination == "sns":
        topic_arn = config.GRAPH_QUERIES_SNS_TOPIC_ARN
        assert topic_arn is not None, (
            "To stream to SNS, the GRAPH_QUERIES_SNS_TOPIC_ARN environment variable must be defined."
        )

        transformer.stream_to_sns(topic_arn, entity_type, sample_size)
    elif stream_destination == "local":
        file_name = f"{transformer_type}__{entity_type}.csv"
        file_path = transformer.stream_to_local_file(
            file_name, entity_type, sample_size
        )
        print(f"Data streamed to local file: {file_path}")
    elif stream_destination == "void":
        for _ in transformer.stream(entity_type, sample_size):
            pass
        # Stop processing to ensure threads are terminated when sample_size is reached
        if sample_size is not None:
            transformer.source.stop_processing()
    else:
        raise ValueError("Unsupported stream destination.")


def lambda_handler(event: LambdaEvent, context: typing.Any) -> None:
    stream_destination = event["stream_destination"]
    transformer_type = event["transformer_type"]
    entity_type = event["entity_type"]
    pipeline_date = event["pipeline_date"]
    sample_size = event.get("sample_size")

    handler(
        stream_destination, transformer_type, entity_type, pipeline_date, sample_size
    )


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
        "--pipeline-date",
        type=str,
        help="The pipeline to extract data from. Will default to `None`.",
        required=False,
    )
    parser.add_argument(
        "--sample-size",
        type=int,
        help="How many entities to stream. If not specified, streaming will continue until the source is exhausted.",
    )
    parser.add_argument(
        "--is-local",
        action="store_true",
        help="Whether to run the handler in local mode",
    )
    args = parser.parse_args()

    handler(**args.__dict__)


if __name__ == "__main__":
    local_handler()
