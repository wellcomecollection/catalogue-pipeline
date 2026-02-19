#!/usr/bin/env python
import json
import typing
from argparse import ArgumentParser

import structlog
from elasticsearch import Elasticsearch

from clients.neptune_client import NeptuneClient, NeptuneEnvironment
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
from utils.argparse import add_cluster_connection_args, add_pipeline_event_args
from utils.elasticsearch import ElasticsearchMode, get_client
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.reporting import LoaderReport
from utils.steps import create_job_id, run_ecs_handler
from utils.types import IngestorType

logger = structlog.get_logger(__name__)


def create_transformer(
    event: IngestorLoaderLambdaEvent,
    es_client: Elasticsearch,
    neptune_client: NeptuneClient,
) -> ElasticsearchBaseTransformer:
    if event.ingestor_type == "concepts":
        return ElasticsearchConceptsTransformer(event, es_client, neptune_client)
    if event.ingestor_type == "works":
        return ElasticsearchWorksTransformer(event, es_client, neptune_client)

    raise ValueError(f"Unknown transformer type: {event.ingestor_type}")


def handler(
    event: IngestorLoaderLambdaEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
    load_destination: LoadDestination = "s3",
    neptune_environment: NeptuneEnvironment = "prod",
) -> IngestorIndexerLambdaEvent:
    setup_logging(execution_context)

    logger.info(
        "Received event",
        ingestor_type=event.ingestor_type,
        pipeline_date=event.pipeline_date,
        job_id=event.job_id,
    )

    es_client = get_client(
        f"{event.ingestor_type}_ingestor", event.pipeline_date, es_mode
    )
    neptune_client = NeptuneClient(neptune_environment)
    transformer = create_transformer(event, es_client, neptune_client)
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

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="ingestor_loader",
    )

    run_ecs_handler(
        arg_parser=arg_parser,
        handler=handler,
        event_validator=event_validator,
        execution_context=execution_context,
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
    add_pipeline_event_args(
        parser, {"pipeline_date", "index_date_merged", "window", "pit_id"}
    )
    add_cluster_connection_args(parser, {"es_mode", "neptune_environment"})

    parser.add_argument(
        "--ingestor-type",
        type=str,
        choices=typing.get_args(IngestorType),
        help="Which ingestor to run (works or concepts).",
        required=True,
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help='The index date that is being ingested to, will default to "dev".',
        required=False,
        default="dev",
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
        "--pass-objects-to-index",
        action="store_true",
        help="Return the list of generated objects in the loader response.",
    )

    args = parser.parse_args()
    event = IngestorLoaderLambdaEvent.from_argparser(args)
    handler(
        event,
        None,
        args.es_mode,
        args.load_destination,
        args.neptune_environment,
    )


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
