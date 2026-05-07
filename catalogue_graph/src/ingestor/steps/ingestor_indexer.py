#!/usr/bin/env python
import json
import typing
from argparse import ArgumentParser
from collections.abc import Generator

import boto3
import structlog

import config
import utils.elasticsearch
from ingestor.models.indexable.concept import IndexableConcept
from ingestor.models.indexable.image import IndexableImage
from ingestor.models.indexable.record import IndexableRecord
from ingestor.models.indexable.work import IndexableWork
from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorIndexerMonitorLambdaEvent,
    IngestorIndexerObject,
    IngestorStepEvent,
)
from utils.argparse import add_pipeline_event_args, validate_es_mode_for_writes
from utils.aws import df_from_s3_parquet, dicts_from_s3_jsonl
from utils.elasticsearch import (
    ElasticsearchMode,
    get_standard_index_name,
    index_es_batch,
)
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.reporting import IndexerReport
from utils.steps import create_job_id, run_ecs_handler
from utils.types import IngestorType

logger = structlog.get_logger(__name__)

RECORD_CLASSES: dict[IngestorType, type[IndexableRecord]] = {
    "concepts": IndexableConcept,
    "works": IndexableWork,
    "images": IndexableImage,
}


def _get_objects_to_index(
    base_event: IngestorStepEvent,
) -> Generator[IngestorIndexerObject]:
    logger.info("Listing S3 objects to index")
    bucket_name = config.CATALOGUE_GRAPH_S3_BUCKETS[base_event.environment]
    prefix = base_event.get_path_prefix()
    load_format = base_event.load_format

    logger.info(
        "Processing files from S3",
        bucket=bucket_name,
        prefix=prefix,
        load_format=load_format,
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
        version = int(datum.get_modified_time().timestamp() * 1000)  # epoch millis

        # Documents whose modified date is set to the start of the Unix epoch will have a version of 0.
        # We floor this to 100 for backward compatibility with documents which use Elasticsearch's default versioning
        # (which increments every time a given document is reindexed).
        # This won't be needed after we do a full reindex.
        version = max(100, version)

        yield {
            "_index": index_name,
            "_id": datum.get_id(),
            "_source": source,
            "_version": version,
            "_version_type": "external_gte",
        }


def get_indexable_data(
    event: IngestorIndexerLambdaEvent, s3_uri: str
) -> list[IndexableRecord]:
    record_class = RECORD_CLASSES[event.ingestor_type]

    if event.load_format == "parquet":
        data = df_from_s3_parquet(s3_uri).to_dicts()
    else:
        data = dicts_from_s3_jsonl(s3_uri)

    logger.info("Extracted records from S3", count=len(data), s3_uri=s3_uri)
    return [record_class.from_raw_document(row) for row in data]


def handler(
    event: IngestorIndexerLambdaEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
) -> IngestorIndexerMonitorLambdaEvent:
    setup_logging(execution_context)

    logger.info(
        "Received event",
        ingestor_type=event.ingestor_type,
        pipeline_date=event.pipeline_date,
        job_id=event.job_id,
    )

    objects_to_index = event.objects_to_index or _get_objects_to_index(event)
    es_client = utils.elasticsearch.get_client(
        f"{event.ingestor_type}_ingestor", event.pipeline_date, es_mode
    )
    index_name = get_standard_index_name(
        f"{event.ingestor_type}-indexed", event.index_date
    )

    total_success_count = 0
    all_es_errors = []

    for s3_object in objects_to_index:
        indexable_data = get_indexable_data(event, s3_object.s3_uri)

        logger.info(
            "Loading documents to ES index",
            count=len(indexable_data),
            ingestor_type=event.ingestor_type,
            index_name=index_name,
        )

        operations = list(generate_operations(index_name, indexable_data))
        success_count, es_errors = index_es_batch(es_client, operations)
        total_success_count += success_count
        all_es_errors += es_errors

    event_payload = event.model_dump(exclude={"objects_to_index"})

    logger.info("Preparing indexer pipeline report")
    report = IndexerReport(**event_payload, success_count=total_success_count)
    report.publish()

    if all_es_errors:
        logger.error(
            "Bulk indexing errors encountered",
            total_errors=len(all_es_errors),
            first_errors=all_es_errors[:5],
        )
        raise RuntimeError(f"Bulk indexing failed with {len(all_es_errors)} error(s)")

    return IngestorIndexerMonitorLambdaEvent(
        **event_payload,
        success_count=total_success_count,
    )


def lambda_handler(event: dict, context: typing.Any) -> dict[str, typing.Any]:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="ingestor_indexer",
    )
    return handler(IngestorIndexerLambdaEvent(**event), execution_context).model_dump(
        mode="json"
    )


def event_validator(raw_input: str) -> IngestorIndexerLambdaEvent:
    event = json.loads(raw_input)
    if "job_id" not in event:
        event["job_id"] = create_job_id()

    return IngestorIndexerLambdaEvent.model_validate(event)


def local_handler(parser: ArgumentParser) -> None:
    add_pipeline_event_args(
        parser,
        {
            "pipeline_date",
            "index_date_merged",
            "index_date_augmented",
            "window",
            "ids",
            "environment",
            "es_mode",
        },
    )
    parser.add_argument(
        "--ingestor-type",
        type=str,
        choices=typing.get_args(IngestorType),
        help="The type of the records being ingested",
        required=True,
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help="The index date that is being ingested to, will default to 'dev'.",
        required=False,
        default="dev",
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
    validate_es_mode_for_writes(parser, args)
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
        # This will automatically use `es_mode=private`
        run_ecs_handler(
            arg_parser=parser,
            handler=handler,
            event_validator=event_validator,
            pipeline_step="ingestor_indexer",
        )
