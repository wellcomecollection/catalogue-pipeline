#!/usr/bin/env python

import argparse
import typing

import config
from models.events import (
    EntityType,
    ExtractorEvent,
    StreamDestination,
    TransformerType,
)
from transformers.base_transformer import BaseTransformer
from transformers.create_transformer import create_transformer
from utils.aws import get_neptune_client


def handler(event: ExtractorEvent, is_local: bool = False) -> None:
    print(
        f"Transforming {event.sample_size or 'all'} {event.entity_type} using the {event.transformer_type} "
        f"transformer and streaming them into {event.stream_destination}."
    )
    if event.pipeline_date == "dev":
        print("No pipeline date specified. Will connect to a local Elasticsearch instance.")
    elif is_local:
        print("Will connect to the public Elasticsearch host (is_local=True).")
    else:
        print("Will connect to the private Elasticsearch host (is_local=False).")

    transformer: BaseTransformer = create_transformer(
        event.transformer_type,
        event.entity_type,
        event.pipeline_date,
        event.window,
        is_local=is_local,
    )

    if event.stream_destination == "graph":
        client = get_neptune_client(is_local)
        transformer.stream_to_graph(client, event.entity_type, event.sample_size)
    elif event.stream_destination == "s3":
        s3_uri = event.get_bulk_load_s3_uri()
        transformer.stream_to_s3(s3_uri, event.entity_type, event.sample_size)
        print(f"Data streamed to S3 file: '{s3_uri}'.")
    elif event.stream_destination == "sns":
        topic_arn = config.GRAPH_QUERIES_SNS_TOPIC_ARN
        if topic_arn is None:
            raise ValueError(
                "To stream to SNS, the GRAPH_QUERIES_SNS_TOPIC_ARN environment variable must be defined."
            )

        transformer.stream_to_sns(topic_arn, event.entity_type, event.sample_size)
    elif event.stream_destination == "local":
        file_path = event.get_bulk_load_file_path()
        full_file_path = transformer.stream_to_local_file(
            file_path, event.entity_type, event.sample_size
        )
        print(f"Data streamed to local file: '{full_file_path}'.")
    elif event.stream_destination == "void":
        for _ in transformer.stream(event.entity_type, event.sample_size):
            pass
    else:
        raise ValueError("Unsupported stream destination.")


def lambda_handler(event: dict, context: typing.Any) -> None:
    handler(ExtractorEvent(**event))


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
        help="The pipeline to extract data from. Will default to 'dev'.",
        default="dev",
        required=False,
    )
    parser.add_argument(
        "--sample-size",
        type=int,
        help="How many entities to stream. If not specified, streaming will continue until the source is exhausted.",
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
        "--is-local",
        action="store_true",
        help="Whether to run the handler in local mode",
    )
    args = parser.parse_args()
    event = ExtractorEvent.from_argparser(args)

    handler(event, is_local=args.is_local)


if __name__ == "__main__":
    local_handler()
