#!/usr/bin/env python

"""
Example of how to use structlog-based logging in extractor.py
"""

import json
import typing
from argparse import ArgumentParser

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
from utils.steps import run_ecs_handler
from utils.logger import ExecutionContext, get_logger, setup_logging

trace_id = "some-value-passed-down-state-machine-steps"


def raw_event(raw_input: str) -> ExtractorEvent:
    event = json.loads(raw_input)
    return ExtractorEvent(**event)


def handler(event: ExtractorEvent, is_local: bool = False) -> None:
    logger = get_logger("graph-extractor")

    logger.info(
        "Starting extraction",
        sample_size=event.sample_size,
        transformer_type=event.transformer_type,
        entity_type=event.entity_type,
        stream_destination=event.stream_destination,
        pipeline_date=getattr(event, "pipeline_date", None),
    )

    transformer: BaseTransformer = create_transformer(
        event,
        es_mode="public" if is_local else "private",
    )

    try:
        if event.stream_destination == "graph":
            client = get_neptune_client(is_local)
            logger.info("Streaming to Neptune graph", destination="graph")
            transformer.stream_to_graph(client, event.entity_type, event.sample_size)

        elif event.stream_destination == "s3":
            s3_uri = event.get_bulk_load_s3_uri()
            logger.info("Streaming to S3", destination="s3", s3_uri=s3_uri)
            transformer.stream_to_s3(s3_uri, event.entity_type, event.sample_size)

        elif event.stream_destination == "sns":
            topic_arn = config.GRAPH_QUERIES_SNS_TOPIC_ARN
            if topic_arn is None:
                error_msg = "To stream to SNS, the GRAPH_QUERIES_SNS_TOPIC_ARN environment variable must be defined"
                logger.error("Missing SNS configuration", error=error_msg)
                raise ValueError(error_msg)
            logger.info("Streaming to SNS", destination="sns", topic_arn=topic_arn)
            transformer.stream_to_sns(topic_arn, event.entity_type, event.sample_size)

        elif event.stream_destination == "local":
            file_path = event.get_bulk_load_file_path()
            logger.info(
                "Streaming to local file", destination="local", file_path=file_path
            )
            transformer.stream_to_local_file(
                file_path, event.entity_type, event.sample_size
            )

        elif event.stream_destination == "void":
            logger.info("Streaming to void (discarding output)", destination="void")
            for _ in transformer.stream(event.entity_type, event.sample_size):
                pass

        else:
            raise ValueError(
                f"Unsupported stream destination: {event.stream_destination}"
            )

        logger.info("Extraction completed successfully")

    except Exception as e:
        logger.error(
            "Extraction failed",
            error_type=type(e).__name__,
            error_message=str(e),
            transformer_type=event.transformer_type,
            entity_type=event.entity_type,
            stream_destination=event.stream_destination,
            exc_info=True,
        )
        raise


def lambda_handler(event: dict, context: typing.Any) -> None:
    # Use hardcoded trace_id for now
    logger = setup_logging(
        ExecutionContext(trace_id=trace_id, pipeline_step="graph_extractor")
    )

    logger.info("Lambda invocation started")

    handler(ExtractorEvent(**event))

    logger.info("Lambda invocation completed successfully")


def event_validator(raw_input: str) -> ExtractorEvent:
    event = json.loads(raw_input)
    return ExtractorEvent(**event)


def ecs_handler(arg_parser: ArgumentParser) -> None:
    run_ecs_handler(
        arg_parser=arg_parser,
        handler=handler,
        event_validator=event_validator,
    )

    # Use hardcoded trace_id for now
    setup_logging(
        ExecutionContext(trace_id=trace_id, pipeline_step="graph_extractor")
    )


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

    # Use hardcoded trace_id for now
    logger = setup_logging(
        ExecutionContext(trace_id=trace_id, pipeline_step="graph_extractor"),
        is_local=True,
    )

    logger.info(
        "Local handler started",
        transformer_type=event.transformer_type,
        entity_type=event.entity_type,
    )

    handler(event, is_local=args.is_local)
    logger.info("Local handler completed successfully")


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
