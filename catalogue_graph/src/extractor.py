#!/usr/bin/env python

import json
import typing
from argparse import ArgumentParser

import structlog

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
from utils.logger import ExecutionContext, setup_logging
from utils.steps import run_ecs_handler


def handler(event: ExtractorEvent, is_local: bool = False) -> None:
    setup_logging(
        ExecutionContext(
            trace_id="logging test",
            pipeline_step="graph_extractor",
        )
    )

    structlog.get_logger(__name__).info(
        f"ECS extractor task starting for {event.sample_size or 'all'} entities.",
        transformer_type=event.transformer_type,
        entity_type=event.entity_type,
        stream_destination=event.stream_destination,
    )

    transformer: BaseTransformer = create_transformer(
        event,
        es_mode="public" if is_local else "private",
    )

    if event.stream_destination == "graph":
        client = get_neptune_client(is_local)
        transformer.stream_to_graph(client, event.entity_type, event.sample_size)
    elif event.stream_destination == "s3":
        s3_uri = event.get_s3_uri()
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
        file_path = event.get_file_path()
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


def event_validator(raw_input: str) -> ExtractorEvent:
    event = json.loads(raw_input)
    return ExtractorEvent(**event)


def ecs_handler(arg_parser: ArgumentParser) -> None:
    run_ecs_handler(
        arg_parser=arg_parser,
        handler=handler,
        event_validator=event_validator,
    )

    structlog.get_logger(__name__).info("ECS extractor task completed successfully.")


def local_handler(parser: ArgumentParser) -> None:
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
        default="s3",
        required=False,
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="The pipeline to extract data from. Will default to 'dev'.",
        default="dev",
        required=False,
    )
    parser.add_argument(
        "--index-date-merged",
        type=str,
        help="The merged index date to read from, will default to pipeline date.",
        required=False,
    )
    parser.add_argument(
        "--pit-id",
        type=str,
        help="An Elasticsearch point in time ID to use when extracting data from the merged index.",
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

    local_args = parser.parse_args()
    event = ExtractorEvent.from_argparser(local_args)

    handler(event, is_local=args.is_local)


if __name__ == "__main__":
    parser = ArgumentParser(description="")
    parser.add_argument(
        "--is-local",
        action="store_true",
        help="Whether to run the handler in local mode",
    )
    args, _ = parser.parse_known_args()

    if args.is_local:
        local_handler(parser)
    else:
        ecs_handler(parser)
