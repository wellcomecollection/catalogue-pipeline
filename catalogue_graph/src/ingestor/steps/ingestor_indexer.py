#!/usr/bin/env python

import argparse
import typing
from collections.abc import Generator

import elasticsearch.helpers
from pydantic import BaseModel

import utils.elasticsearch
from ingestor.models.indexable_concept import IndexableConcept
from ingestor.models.indexable_work import IndexableWork
from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorIndexerMonitorLambdaEvent,
    IngestorIndexerObject,
)
from utils.aws import df_from_s3_parquet
from utils.elasticsearch import get_standard_index_name
from utils.types import IngestorType

RECORD_CLASSES: dict[IngestorType, typing.Type[BaseModel]] = {
    "concepts": IndexableConcept,
    "works": IndexableWork
}


class IngestorIndexerConfig(BaseModel):
    is_local: bool = False


def generate_operations(
        index_name: str,
        indexable_data: list[IndexableConcept] | list[IndexableWork]) -> Generator[dict]:
    for datum in indexable_data:
        yield {
            "_index": index_name,
            "_id": datum.query.id,
            "_source": datum.model_dump(),
        }


def load_data(
        ingestor_type: IngestorType,
        indexable_data: list[IndexableConcept] | list[IndexableWork],
        pipeline_date: str | None,
        index_date: str | None,
        is_local: bool,
) -> int:
    index_name = get_standard_index_name(f"{ingestor_type}-indexed", index_date)
    print(f"Loading {len(indexable_data)} Indexable {ingestor_type} to ES index: {index_name} ...")
    es = utils.elasticsearch.get_client("concept_ingestor", pipeline_date, is_local)
    success_count, _ = elasticsearch.helpers.bulk(es, generate_operations(index_name, indexable_data))

    return success_count


def handler(
        event: IngestorIndexerLambdaEvent, config: IngestorIndexerConfig
) -> IngestorIndexerMonitorLambdaEvent:
    print(f"Received event: {event} with config {config}")

    df = df_from_s3_parquet(event.object_to_index.s3_uri)
    print(f"Extracted {len(df)} records.")
    record_class = RECORD_CLASSES[event.ingestor_type]

    success_count = load_data(
        ingestor_type=event.ingestor_type,
        indexable_data=[record_class.model_validate(row) for row in df.to_dicts()],
        pipeline_date=event.pipeline_date,
        index_date=event.index_date,
        is_local=config.is_local,
    )

    print(f"Successfully indexed {success_count} documents.")

    return IngestorIndexerMonitorLambdaEvent(
        **event.model_dump(),
        success_count=success_count,
    )


def lambda_handler(event: dict, context: typing.Any) -> dict[str, typing.Any]:
    return handler(
        IngestorIndexerLambdaEvent(**event), IngestorIndexerConfig()
    ).model_dump()


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
        help="The concepts index date that is being ingested to, will default to 'dev'.",
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="The ID of the job to process, will default to 'dev'.",
        required=False,
        default="dev",
    )
    args = parser.parse_args()

    event = IngestorIndexerLambdaEvent(
        **args.__dict__,
        object_to_index=IngestorIndexerObject(s3_uri=args.s3_uri),
    )
    config = IngestorIndexerConfig(is_local=True)

    handler(event, config)


if __name__ == "__main__":
    local_handler()
