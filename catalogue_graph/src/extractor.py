#!/usr/bin/env python

import json
import typing
from argparse import ArgumentParser

import structlog

from models.events import (
    EntityType,
    ExtractorEvent,
    StreamDestination,
    TransformerType,
)
from transformers.base_transformer import BaseTransformer
from transformers.create_transformer import create_transformer
from utils.argparse import add_cluster_connection_args, add_pipeline_event_args
from utils.elasticsearch import ElasticsearchMode, get_client
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.steps import run_ecs_handler

logger = structlog.get_logger(__name__)


def handler(
    event: ExtractorEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
) -> None:
    setup_logging(execution_context)

    logger.info(
        f"ECS extractor task starting for {event.sample_size or 'all'} entities.",
        transformer_type=event.transformer_type,
        entity_type=event.entity_type,
        stream_destination=event.stream_destination,
    )

    es_client = get_client("graph_extractor", event.pipeline_date, es_mode)
    transformer: BaseTransformer = create_transformer(event, es_client)

    if event.stream_destination == "s3":
        s3_uri = event.get_s3_uri()
        transformer.stream_to_s3(s3_uri, event.entity_type, event.sample_size)
        logger.info("Data streamed to S3", s3_uri=s3_uri)
    elif event.stream_destination == "local":
        file_path = event.get_file_path()
        full_file_path = transformer.stream_to_local_file(
            file_path, event.entity_type, event.sample_size
        )
        logger.info("Data streamed to local file", file_path=full_file_path)
    elif event.stream_destination == "void":
        for _ in transformer.stream(event.entity_type, event.sample_size):
            pass
    else:
        raise ValueError("Unsupported stream destination.")


def lambda_handler(event: dict, context: typing.Any) -> None:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="graph_extractor",
    )
    handler(ExtractorEvent(**event), execution_context)


def event_validator(raw_input: str) -> ExtractorEvent:
    event = json.loads(raw_input)
    return ExtractorEvent(**event)


def ecs_handler(arg_parser: ArgumentParser) -> None:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="graph_extractor",
    )

    run_ecs_handler(
        arg_parser=arg_parser,
        handler=handler,
        event_validator=event_validator,
        execution_context=execution_context,
    )

    logger.info("ECS extractor task completed successfully")


def local_handler(parser: ArgumentParser) -> None:
    add_pipeline_event_args(
        parser, {"pipeline_date", "index_date_merged", "window", "pit_id"}
    )
    add_cluster_connection_args(parser, {"es_mode"})
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
        "--sample-size",
        type=int,
        help="How many entities to stream. If not specified, streaming will continue until the source is exhausted.",
    )

    local_args = parser.parse_args()
    event = ExtractorEvent.from_argparser(local_args)

    handler(event, es_mode=args.es_mode)


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
