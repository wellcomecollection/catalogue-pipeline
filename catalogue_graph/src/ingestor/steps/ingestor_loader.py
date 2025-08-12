#!/usr/bin/env python

import argparse
import typing

from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorLoaderLambdaEvent,
)
from ingestor.transformers.base_transformer import ElasticsearchBaseTransformer
from ingestor.transformers.concepts_transformer import ElasticsearchConceptsTransformer
from utils.types import IngestorLoadFormat, IngestorType


class IngestorLoaderConfig(BaseModel):
    loader_s3_bucket: str = INGESTOR_S3_BUCKET
    loader_s3_prefix: str = INGESTOR_S3_PREFIX
    is_local: bool = False
    load_format: IngestorLoadFormat = "parquet"


def create_transformer(
    event: IngestorLoaderLambdaEvent, config: IngestorLoaderConfig
) -> ElasticsearchBaseTransformer:
    if event.ingestor_type == "concepts":
        return ElasticsearchConceptsTransformer(
            event.start_offset, event.end_index, config.is_local
        )

    raise ValueError(f"Unknown transformer type: {event.ingestor_type}")


def get_filename(event: IngestorLoaderLambdaEvent) -> str:
    return f"{str(event.start_offset).zfill(8)}-{str(event.end_index).zfill(8)}"


def handler(
    event: IngestorLoaderLambdaEvent, config: IngestorLoaderConfig
) -> IngestorIndexerLambdaEvent:
    print(f"Received event: {event} with config {config}")

    pipeline_date = event.pipeline_date or "dev"
    index_date = event.index_date or "dev"

    transformer = create_transformer(event, config)
    s3_object_key = f"{pipeline_date}/{index_date}/{event.job_id}/{get_filename(event)}.{config.load_format}"
    s3_uri = f"s3://{config.loader_s3_bucket}/{config.loader_s3_prefix}_{event.ingestor_type}/{s3_object_key}"
    result = transformer.load_documents_to_s3(
        s3_uri=s3_uri, load_format=config.load_format
    )

    return IngestorIndexerLambdaEvent(
        ingestor_type=event.ingestor_type,
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=event.job_id,
        object_to_index=result,
    )


def lambda_handler(event: IngestorLoaderLambdaEvent, context: typing.Any) -> dict:
    return handler(
        IngestorLoaderLambdaEvent.model_validate(event), IngestorLoaderConfig()
    ).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--ingestor-type",
        type=str,
        choices=typing.get_args(IngestorType),
        help="Which ingestor to run.",
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
        choices=["s3", "local"],
        default="s3",
    )
    parser.add_argument(
        "--load-format",
        type=str,
        help='The format of loaded documents, will default to "parquet".',
        required=False,
        choices=["parquet", "jsonl"],
        default="parquet",
    )

    args = parser.parse_args()

    event = IngestorLoaderLambdaEvent(**args.__dict__)
    config = IngestorLoaderConfig(is_local=True, load_format=args.load_format)

    if args.load_destination == "local":
        transformer = create_transformer(event, config)
        file_path = transformer.load_documents_to_local_file(
            get_filename(event), config.load_format
        )
        print(f"Documents loaded to local file: {file_path}")
    else:
        handler(event, config)


if __name__ == "__main__":
    local_handler()
