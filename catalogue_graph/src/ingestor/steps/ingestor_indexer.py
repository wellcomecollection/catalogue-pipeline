#!/usr/bin/env python
import json
import typing
from argparse import ArgumentParser
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
from utils.elasticsearch import ElasticsearchMode, get_standard_index_name
from utils.steps import create_job_id
from utils.types import IngestorType

RECORD_CLASSES: dict[IngestorType, type[IndexableRecord]] = {
    "concepts": IndexableConcept,
    "works": IndexableWork,
}


def _get_objects_to_index(
    base_event: IngestorStepEvent,
) -> Generator[IngestorIndexerObject]:
    print("Listing S3 objects to index...")
    bucket_name = config.CATALOGUE_GRAPH_S3_BUCKET
    prefix = base_event.get_path_prefix()
    load_format = base_event.load_format

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
    index_date: str,
    es_mode: ElasticsearchMode,
) -> int:
    index_name = get_standard_index_name(f"{ingestor_type}-indexed", index_date)
    print(
        f"Loading {len(indexable_data)} Indexable {ingestor_type} to ES index: {index_name} ..."
    )
    es = utils.elasticsearch.get_client(
        f"{ingestor_type}_ingestor", pipeline_date, es_mode
    )
    success_count, _ = elasticsearch.helpers.bulk(
        es, generate_operations(index_name, indexable_data)
    )

    return success_count


def handler(
    event: IngestorIndexerLambdaEvent, es_mode: ElasticsearchMode = "private"
) -> IngestorIndexerMonitorLambdaEvent:
    print(f"Received event: {event}.")

    record_class = RECORD_CLASSES[event.ingestor_type]

    objects_to_index = event.objects_to_index or _get_objects_to_index(event)

    total_success_count = 0
    for s3_object in objects_to_index:
        if event.load_format == "parquet":
            data = df_from_s3_parquet(s3_object.s3_uri).to_dicts()
        else:
            data = dicts_from_s3_jsonl(s3_object.s3_uri)

        print(f"Extracted {len(data)} records.")
        success_count = load_data(
            ingestor_type=event.ingestor_type,
            indexable_data=[record_class.from_raw_document(row) for row in data],
            pipeline_date=event.pipeline_date,
            index_date=event.index_date,
            es_mode=es_mode,
        )
        print(f"Successfully indexed {success_count} documents.")

        total_success_count += success_count

    return IngestorIndexerMonitorLambdaEvent(
        **event.model_dump(),
        success_count=total_success_count,
    )


def lambda_handler(event: dict, context: typing.Any) -> dict[str, typing.Any]:
    return handler(IngestorIndexerLambdaEvent(**event)).model_dump(mode="json")


def raw_event(raw_input: str) -> IngestorIndexerLambdaEvent:
    event = json.loads(raw_input)
    if "job_id" not in event:
        event["job_id"] = create_job_id()

    return IngestorIndexerLambdaEvent.model_validate(event)


def ecs_handler(parser: ArgumentParser) -> None:
    parser.add_argument(
        "--event",
        type=raw_event,
        help="Raw event in JSON format.",
        required=True,
    )
    parser.add_argument(
        "--task-token",
        type=str,
        help="The Step Functions task token for reporting success or failure.",
        required=False,
    )
    parser.add_argument(
        "--es-mode",
        type=str,
        help="Where to extract Elasticsearch documents. Use 'public' to connect to the production cluster.",
        required=False,
        choices=["private", "local", "public"],
        default="private",
    )

    ecs_args = parser.parse_args()

    task_token = ecs_args.task_token
    if task_token:
        print(
            "Received TASK_TOKEN in ECS arguments, will report back to Step Functions."
        )

    try:
        result = handler(event=ecs_args.event, es_mode=ecs_args.es_mode)
        output = result.model_dump_json()

        if task_token:
            print("Sending task success to Step Functions.")
            stepfunctions_client = boto3.client("stepfunctions")
            stepfunctions_client.send_task_success(taskToken=task_token, output=output)
        else:
            print(
                "No TASK_TOKEN found in environment variables, skipping send_task_success."
            )
            print(f"Result: {output}")

    except Exception as e:
        error_output = json.dumps({"error": str(e)})

        if task_token:
            print(f"Sending task failure to Step Functions: {error_output}")
            stepfunctions_client = boto3.client("stepfunctions")
            stepfunctions_client.send_task_failure(
                taskToken=task_token, error="IngestorLoaderError", cause=error_output
            )
        else:
            raise


def local_handler(parser: ArgumentParser) -> None:
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
    parser.add_argument(
        "--es-mode",
        type=str,
        help="Where to index documents. Use 'public' to connect to the production cluster.",
        required=False,
        choices=["local", "public"],
        default="local",
    )

    args = parser.parse_args()
    base_event = IngestorStepEvent.from_argparser(args)

    event = IngestorIndexerLambdaEvent(**base_event.model_dump())
    handler(event, es_mode=args.es_mode)


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
