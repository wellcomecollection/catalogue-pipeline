#!/usr/bin/env python

import argparse
import typing
from collections.abc import Generator

import boto3
import polars as pl
import smart_open
from elasticsearch.helpers import bulk
from polars import DataFrame
from pydantic import BaseModel

from config import INGESTOR_PIPELINE_DATE
from models.catalogue_concept import CatalogueConcept
from models.indexable_concept import IndexableConcept
from utils import elasticsearch


class IngestorIndexerLambdaEvent(BaseModel):
    s3_url: str


class IngestorIndexerConfig(BaseModel):
    pipeline_date: str | None = INGESTOR_PIPELINE_DATE
    is_local: bool = False


def extract_data(s3_url: str) -> DataFrame:
    print("Extracting data from S3 ...")
    transport_params = {"client": boto3.client("s3")}

    with smart_open.open(s3_url, "r", transport_params=transport_params) as f:
        df = pl.read_parquet(f)
        print(f"Extracted {len(df)} records.")

    return df


def transform_data(df: DataFrame) -> list[IndexableConcept]:
    print("Transforming data: CatalogueConcept -> IndexableConcept -> JSON -> str ...")
    catalogue_concepts = [CatalogueConcept.model_validate(row) for row in df.to_dicts()]
    return [IndexableConcept.from_concept(concept) for concept in catalogue_concepts]


def load_data(
    concepts: list[IndexableConcept], pipeline_date: str | None, is_local: bool
) -> int:
    index_name = (
        "concepts-indexed"
        if pipeline_date is None
        else f"concepts-indexed-{pipeline_date}"
    )
    print(f"Loading {len(concepts)} IndexableConcept to ES index: {index_name} ...")
    es = elasticsearch.get_client(pipeline_date, is_local)

    def generate_data() -> Generator[dict]:
        for concept in concepts:
            yield {
                "_index": index_name,
                "_id": concept.query.id,
                "_source": concept.model_dump_json(),
            }

    success_count, _ = bulk(es, generate_data())

    print(f"Successfully indexed {success_count} documents.")
    return success_count


def handler(event: IngestorIndexerLambdaEvent, config: IngestorIndexerConfig) -> int:
    print(f"Received event: {event} with config {config}")
    
    extracted_data = extract_data(event.s3_url)
    transformed_data = transform_data(extracted_data)
    success_count = load_data(
        concepts=transformed_data, 
        pipeline_date=config.pipeline_date, 
        is_local=config.is_local
    )

    print("Data loaded successfully.")

    return success_count


def lambda_handler(event: IngestorIndexerLambdaEvent, context: typing.Any) -> int:
    return handler(
        IngestorIndexerLambdaEvent.model_validate(event), IngestorIndexerConfig()
    )


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--s3-url",
        type=str,
        help="The location of the shard to process.",
        required=True,
    )
    args = parser.parse_args()

    event = IngestorIndexerLambdaEvent(**args.__dict__)
    config = IngestorIndexerConfig(is_local=True)

    handler(event, config)


if __name__ == "__main__":
    local_handler()
