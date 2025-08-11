#!/usr/bin/env python
import argparse
import typing
from typing import Any

from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorLoaderLambdaEvent,
)
from ingestor.transformers.base_transformer import ElasticsearchBaseTransformer
from ingestor.transformers.concepts_transformer import ElasticsearchConceptsTransformer
from ingestor.transformers.works_transformer import ElasticsearchWorksTransformer
from utils.types import ElasticsearchTransformerType


class IngestorLoaderConfig(BaseModel):
    loader_s3_bucket: str = INGESTOR_S3_BUCKET
    loader_s3_prefix: str = INGESTOR_S3_PREFIX
    is_local: bool = False


def create_transformer(
    event: IngestorLoaderLambdaEvent, config: IngestorLoaderConfig
) -> ElasticsearchBaseTransformer:
    if event.transformer_type == "concepts":
        return ElasticsearchConceptsTransformer(
            event.start_offset, event.end_index, config.is_local
        )
    if event.transformer_type == "works":
        return ElasticsearchWorksTransformer(
            event.start_offset, event.end_index, config.is_local
        )
    raise ValueError(f"Unknown transformer type: {event.transformer_type}")


def get_filename(event: IngestorLoaderLambdaEvent) -> str:
    return f"{str(event.start_offset).zfill(8)}-{str(event.end_index).zfill(8)}"


def handler(
    event: IngestorLoaderLambdaEvent, config: IngestorLoaderConfig
) -> IngestorIndexerLambdaEvent:
    print(f"Received event: {event} with config {config}")

    pipeline_date = event.pipeline_date or "dev"
    index_date = event.index_date or "dev"

    transformer = create_transformer(event, config)

    s3_object_key = (
        f"{pipeline_date}/{index_date}/{event.job_id}/{get_filename(event)}.parquet"
    )
    s3_uri = f"s3://{config.loader_s3_bucket}/{config.loader_s3_prefix}_{event.transformer_type}/{s3_object_key}"
    result = transformer.load_documents_to_s3(s3_uri=s3_uri)

    return IngestorIndexerLambdaEvent(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=event.job_id,
        object_to_index=result,
    )


def lambda_handler(event: IngestorLoaderLambdaEvent, context: Any) -> dict:
    return handler(
        IngestorLoaderLambdaEvent.model_validate(event), IngestorLoaderConfig()
    ).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--transformer-type",
        type=str,
        choices=typing.get_args(ElasticsearchTransformerType),
        help="Which transformer to load data from.",
        required=True,
    )
    parser.add_argument(
        "--start-offset",
        type=int,
        help="The start index of the records to process.",
        required=False,
        default=0,
    )
    parser.add_argument(
        "--end-index",
        type=int,
        help="The end index of the records to process.",
        required=False,
        default=100,
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help='The job identifier used in the S3 path, will default to "dev".',
        required=False,
        default="dev",
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
        "--load-destination",
        type=str,
        help='The destination to load the data to, will default to "s3".',
        required=False,
        choices=["s3", "local", "local_json"],
        default="s3",
    )

    args = parser.parse_args()
    event = IngestorLoaderLambdaEvent(**args.__dict__)
    config = IngestorLoaderConfig(is_local=True)

    if args.load_destination == "local":
        transformer = create_transformer(event, config)
        transformer.load_documents_to_local_file(get_filename(event))
    else:
        handler(event, config)


if __name__ == "__main__":
    local_handler()
