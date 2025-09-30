#!/usr/bin/env python
import argparse
import json
import typing
from collections.abc import Generator

import boto3
import elasticsearch.helpers

import config
import utils.elasticsearch
from ingestor.models.indexable import IndexableRecord
from ingestor.models.indexable_concept import IndexableConcept
from ingestor.models.indexable_work import IndexableWork
from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorIndexerMonitorLambdaEvent,
    IngestorIndexerObject,
    IngestorStepEvent,
)
from utils.aws import df_from_s3_parquet, dicts_from_s3_jsonl
from utils.elasticsearch import get_standard_index_name
from utils.types import IngestorType

RECORD_CLASSES: dict[IngestorType, type[IndexableRecord]] = {
    "concepts": IndexableConcept,
    "works": IndexableWork,
}


def generate_operations(
    index_name: str, indexable_data: list[IndexableRecord]
) -> Generator[dict]:
    for datum in indexable_data:
        source = json.loads(datum.model_dump_json(exclude_none=True))
        yield {
            "_index": index_name,
            "_id": datum.get_id(),
            "_source": source,
        }


def load_data(
    ingestor_type: IngestorType,
    indexable_data: list[IndexableRecord],
    pipeline_date: str,
    index_date: str | None,
    is_local: bool,
) -> int:
    index_name = get_standard_index_name(f"{ingestor_type}-indexed", index_date)
    print(
        f"Loading {len(indexable_data)} Indexable {ingestor_type} to ES index: {index_name} ..."
    )
    es = utils.elasticsearch.get_client("concept_ingestor", pipeline_date, is_local)
    success_count, _ = elasticsearch.helpers.bulk(
        es, generate_operations(index_name, indexable_data)
    )

    return success_count


def handler(
    event: IngestorIndexerLambdaEvent, is_local: bool = False
) -> IngestorIndexerMonitorLambdaEvent:
    print(f"Received event: {event}.")

    record_class = RECORD_CLASSES[event.ingestor_type]

    if len(event.objects_to_index) == 0:
        raise ValueError("No files to process.")

    total_success_count = 0
    for s3_object in event.objects_to_index:
        if event.load_format == "parquet":
            data = df_from_s3_parquet(s3_object.s3_uri).to_dicts()
        else:
            data = dicts_from_s3_jsonl(s3_object.s3_uri)

        print(f"Extracted {len(data)} records.")
        success_count = load_data(
            ingestor_type=event.ingestor_type,
            indexable_data=[record_class.model_validate(row) for row in data],
            pipeline_date=event.pipeline_date,
            index_date=event.index_date,
            is_local=is_local,
        )
        print(f"Successfully indexed {success_count} documents.")

        total_success_count += success_count

    return IngestorIndexerMonitorLambdaEvent(
        **event.model_dump(),
        success_count=total_success_count,
    )


def lambda_handler(event: dict, context: typing.Any) -> dict[str, typing.Any]:
    return handler(IngestorIndexerLambdaEvent(**event)).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--ingestor-type",
        type=str,
        choices=["concepts", "works"],
        help="The type of the records being ingested",
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
        help="The index date that is being ingested to, will default to 'dev'.",
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
        help="The ID of the job to process, will default to 'dev'. Full reindex mode only.",
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--load-format",
        type=str,
        help="The format of loaded documents, will default to 'parquet'.",
        required=False,
        choices=["parquet", "jsonl"],
        default="parquet",
    )

    args = parser.parse_args()
    base_event = IngestorStepEvent.from_argparser(args)

    bucket = config.CATALOGUE_GRAPH_S3_BUCKET
    prefix = base_event.get_path_prefix()
    objects_to_index = _get_objects_to_index(bucket, prefix, base_event.load_format)

    event = IngestorIndexerLambdaEvent(
        **base_event.model_dump(), objects_to_index=list(objects_to_index)
    )
    handler(event, is_local=True)


def _get_objects_to_index(
    bucket_name: str, prefix: str, load_format: str
) -> Generator[IngestorIndexerObject]:
    """Manually construct `IngestorIndexerObject` items based on objects in a given S3 location. Local runs only."""
    print(
        f"Will process all {load_format} files prefixed with 's3://{bucket_name}/{prefix}/*'."
    )

    paginator = boto3.client("s3").get_paginator("list_objects_v2")
    for page in paginator.paginate(Bucket=bucket_name, Prefix=prefix):
        for s3_object in page.get("Contents", []):
            if s3_object["Key"].endswith(f".{load_format}"):
                # Given a key like 'some/prefix/00000000-00002070.format', extract '00000000-00002070'
                range_suffix = s3_object["Key"].split("/")[-1].split(".")[0]
                range_start, range_end = map(int, range_suffix.split("-"))

                yield IngestorIndexerObject(
                    s3_uri=f"s3://{bucket_name}/{s3_object['Key']}",
                    content_length=s3_object["Size"],
                    record_count=range_end - range_start,
                )


if __name__ == "__main__":
    local_handler()
