#!/usr/bin/env python

import argparse
import typing
from collections.abc import Generator

import elasticsearch.helpers
from polars import DataFrame
from pydantic import BaseModel

import utils.elasticsearch
from config import INGESTOR_PIPELINE_DATE
from models.catalogue_concept import CatalogueConcept
from models.indexable_concept import IndexableConcept
from utils.aws import df_from_s3_parquet


class IngestorIndexerObject(BaseModel):
    s3_uri: str
    content_length: int | None = None
    record_count: int | None = None


class IngestorIndexerLambdaEvent(BaseModel):
    pipeline_date: str | None = INGESTOR_PIPELINE_DATE
    index_date: str | None
    job_id: str | None = None
    object_to_index: IngestorIndexerObject


class IngestorIndexerConfig(BaseModel):
    is_local: bool = False


def transform_data(df: DataFrame) -> list[IndexableConcept]:
    print("Transforming data: CatalogueConcept -> IndexableConcept -> JSON -> str ...")
    catalogue_concepts = [CatalogueConcept.model_validate(row) for row in df.to_dicts()]
    return [IndexableConcept.from_concept(concept) for concept in catalogue_concepts]


def load_data(
    concepts: list[IndexableConcept],
    pipeline_date: str | None,
    index_date: str | None,
    is_local: bool,
) -> int:
    index_name = (
        "concepts-indexed"
        if pipeline_date is None
        else f"concepts-indexed-{index_date}"
    )

    print(f"Loading {len(concepts)} IndexableConcept to ES index: {index_name} ...")
    es = utils.elasticsearch.get_client(pipeline_date, is_local)

    def generate_data() -> Generator[dict]:
        for concept in concepts:
            yield {
                "_index": index_name,
                "_id": concept.query.id,
                "_source": concept.model_dump(),
            }

    success_count, _ = elasticsearch.helpers.bulk(es, generate_data())

    return success_count


def handler(event: IngestorIndexerLambdaEvent, config: IngestorIndexerConfig) -> int:
    print(f"Received event: {event} with config {config}")

    extracted_data = df_from_s3_parquet(event.object_to_index.s3_uri)
    print(f"Extracted {len(extracted_data)} records.")

    transformed_data = transform_data(extracted_data)
    success_count = load_data(
        concepts=transformed_data,
        pipeline_date=event.pipeline_date,
        index_date=event.index_date,
        is_local=config.is_local,
    )

    print(f"Successfully indexed {success_count} documents.")

    return success_count


def lambda_handler(event: IngestorIndexerLambdaEvent, context: typing.Any) -> int:
    return handler(
        IngestorIndexerLambdaEvent.model_validate(event), IngestorIndexerConfig()
    )


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--s3-uri",
        type=str,
        help="The location of the shard to process, e.g. s3://mybukkit/path/key.parquet",
        required=True,
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="The pipeline that is being ingested to, will default to None.",
        required=False,
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help="The concepts index date that is being ingested to, will default to None.",
        required=False,
    )
    args = parser.parse_args()

    event = IngestorIndexerLambdaEvent(
        pipeline_date=args.pipeline_date,
        index_date=args.index_date,
        object_to_index=IngestorIndexerObject(s3_uri=args.s3_uri),
    )
    config = IngestorIndexerConfig(is_local=True)

    handler(event, config)


if __name__ == "__main__":
    local_handler()
