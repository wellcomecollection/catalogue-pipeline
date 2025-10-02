#!/usr/bin/env python
import argparse
import datetime
import typing

from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorLoaderLambdaEvent,
)
from ingestor.transformers.base_transformer import ElasticsearchBaseTransformer
from ingestor.transformers.concepts_transformer import ElasticsearchConceptsTransformer
from ingestor.transformers.works_transformer import ElasticsearchWorksTransformer
from utils.elasticsearch import ElasticsearchMode
from utils.types import IngestorType


def create_job_id() -> str:
    """Generate a job_id based on the current time using an iso8601 format like 20210701T1300"""
    return datetime.datetime.now().strftime("%Y%m%dT%H%M")


def create_transformer(
    event: IngestorLoaderLambdaEvent, es_mode: ElasticsearchMode
) -> ElasticsearchBaseTransformer:
    if event.ingestor_type == "concepts":
        return ElasticsearchConceptsTransformer(
            event.pipeline_date, event.window, es_mode
        )
    if event.ingestor_type == "works":
        return ElasticsearchWorksTransformer(event.pipeline_date, event.window, es_mode)

    raise ValueError(f"Unknown transformer type: {event.ingestor_type}")


def handler(
    event: IngestorLoaderLambdaEvent, es_mode: ElasticsearchMode = "private"
) -> IngestorIndexerLambdaEvent:
    print(f"Received event: {event}")

    transformer = create_transformer(event, es_mode)
    objects_to_index = transformer.load_documents(event)

    return IngestorIndexerLambdaEvent(
        **event.model_dump(),
        objects_to_index=objects_to_index,
    )


def lambda_handler(event: dict, context: typing.Any) -> dict:
    if "job_id" not in event:
        event["job_id"] = create_job_id()

    return handler(IngestorLoaderLambdaEvent(**event)).model_dump(mode="json")


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
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
        "--es-mode",
        type=str,
        help="Where to extract Elasticsearch documents. Use 'public' to connect to the production cluster.",
        required=False,
        choices=["local", "public"],
        default="local",
    )

    args = parser.parse_args()
    event = IngestorLoaderLambdaEvent.from_argparser(args)

    if args.load_destination == "local":
        transformer = create_transformer(event, args.es_mode)
        transformer.load_documents(event, "local")
    else:
        handler(event, args.es_mode)


if __name__ == "__main__":
    local_handler()
