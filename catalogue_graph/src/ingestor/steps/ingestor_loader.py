#!/usr/bin/env python
import json
import typing
from argparse import ArgumentParser

import structlog

from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorLoaderLambdaEvent,
)
from ingestor.transformers.base_transformer import (
    ElasticsearchBaseTransformer,
    LoadDestination,
)
from ingestor.transformers.concepts_transformer import ElasticsearchConceptsTransformer
from ingestor.transformers.works_transformer import ElasticsearchWorksTransformer
from utils.elasticsearch import ElasticsearchMode
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.reporting import LoaderReport
from utils.steps import create_job_id, run_ecs_handler
from utils.types import IngestorType

logger = structlog.get_logger(__name__)


def create_transformer(
    event: IngestorLoaderLambdaEvent, es_mode: ElasticsearchMode
) -> ElasticsearchBaseTransformer:
    if event.ingestor_type == "concepts":
        return ElasticsearchConceptsTransformer(event, es_mode)
    if event.ingestor_type == "works":
        return ElasticsearchWorksTransformer(event, es_mode)

    raise ValueError(f"Unknown transformer type: {event.ingestor_type}")


def handler(
    event: IngestorLoaderLambdaEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
    load_destination: LoadDestination = "s3",
) -> IngestorIndexerLambdaEvent:
    setup_logging(
        execution_context
        or ExecutionContext(
            trace_id=get_trace_id(),
            pipeline_step="ingestor_loader",
        )
    )

    logger.info(
        "Received event",
        ingestor_type=event.ingestor_type,
        pipeline_date=event.pipeline_date,
        job_id=event.job_id,
    )

    transformer = create_transformer(event, es_mode)
    objects_to_index = transformer.load_documents(event, load_destination)

    event_payload = event.model_dump(exclude={"pass_objects_to_index"})

    record_count = sum(o.record_count for o in objects_to_index)
    total_file_size = sum(o.content_length for o in objects_to_index)

    report = LoaderReport(
        **event_payload,
        record_count=record_count,
        total_file_size=total_file_size,
    )
    report.publish()

    if event.pass_objects_to_index:
        return IngestorIndexerLambdaEvent(
            **event_payload,
            objects_to_index=objects_to_index,
        )

    return IngestorIndexerLambdaEvent(**event_payload)


def event_validator(raw_input: str) -> IngestorLoaderLambdaEvent:
    event = json.loads(raw_input)
    if "job_id" not in event:
        event["job_id"] = create_job_id()

    return IngestorLoaderLambdaEvent.model_validate(event)


def ecs_handler(arg_parser: ArgumentParser) -> None:
    arg_parser.add_argument(
        "--es-mode",
        type=str,
        help="Where to extract Elasticsearch documents. Use 'public' to connect to the production cluster.",
        required=False,
        choices=["private", "local", "public"],
        default="private",
    )

    args, _ = arg_parser.parse_known_args()
    es_mode = args.es_mode

    run_ecs_handler(
        arg_parser=arg_parser,
        handler=handler,
        event_validator=event_validator,
        es_mode=es_mode,
    )

    logger.info("ECS ingestor loader task completed successfully")


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="ingestor_loader",
    )

    if "job_id" not in event:
        event["job_id"] = create_job_id()

    return handler(IngestorLoaderLambdaEvent(**event), execution_context).model_dump(
        mode="json"
    )


def local_handler(parser: ArgumentParser) -> None:
    parser.add_argument(
        "--ingestor-type",
        type=str,
        choices=typing.get_args(IngestorType),
        help="Which ingestor to run (works or concepts).",
        required=True,
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help='The pipeline that is being ingested to, will default to "dev".',
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--index-date-merged",
        type=str,
        help="The merged index date to read from, will default to pipeline date.",
        required=False,
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help='The index date that is being ingested to, will default to "dev".',
        required=False,
        default="dev",
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
        "--job-id",
        type=str,
        help="The ID of the job to process, will use a default based on the current timestamp if not provided.",
        required=False,
        default=create_job_id(),
    )
    parser.add_argument(
        "--load-destination",
        type=str,
        help='The destination to load the data to, will default to "s3".',
        required=False,
        choices=["s3", "local"],
        default="s3",
    )
    parser.add_argument(
        "--load-format",
        type=str,
        help="The format of loaded documents, will default to 'parquet'.",
        required=False,
        choices=["parquet", "jsonl"],
        default="parquet",
    )
    parser.add_argument(
        "--es-mode",
        type=str,
        help="Where to extract Elasticsearch documents. Use 'public' to connect to the production cluster.",
        required=False,
        choices=["local", "public"],
        default="local",
    )
    parser.add_argument(
        "--pass-objects-to-index",
        action="store_true",
        help="Return the list of generated objects in the loader response.",
    )

    args = parser.parse_args()
    event = IngestorLoaderLambdaEvent.from_argparser(args)
    handler(event, args.es_mode, args.load_destination)


if __name__ == "__main__":
    parser: ArgumentParser = ArgumentParser()
    parser.add_argument(
        "--use-cli",
        action="store_true",
        help="Whether to invoke the local CLI handler instead of the ECS handler.",
    )
    args, _ = parser.parse_known_args()

    if args.use_cli:
        local_handler(parser)
    else:
        ecs_handler(parser)
