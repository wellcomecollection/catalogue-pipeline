#!/usr/bin/env python
import json
import typing
from argparse import ArgumentParser
from collections.abc import Generator

import boto3
import elasticsearch.helpers
import structlog

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
from utils.elasticsearch import ElasticsearchMode
from utils.steps import create_job_id
from utils.elasticsearch import ElasticsearchMode, get_standard_index_name
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.reporting import IndexerReport
from utils.steps import create_job_id, run_ecs_handler
from utils.types import IngestorType

logger = structlog.get_logger(__name__)

RECORD_CLASSES: dict[IngestorType, type[IndexableRecord]] = {
    "concepts": IndexableConcept,
    "works": IndexableWork,
}

# getting loaded docs from S3


def _get_objects_to_index(
    base_event: IngestorStepEvent,
) -> Generator[IngestorIndexerObject]:
    logger.info("Listing S3 objects to index")
    bucket_name = config.CATALOGUE_GRAPH_S3_BUCKET
    prefix = base_event.works_source
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
        yield {
            "_index": index_name,
            "_id": datum.get_id(),
            "_source": source,
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
    create_index_mapping: str | None = None,
) -> IngestorIndexerMonitorLambdaEvent:
    setup_logging(execution_context)

    logger.info(
        "Received event",
        ingestor_type=event.ingestor_type,
        pipeline_date=event.pipeline_date,
        job_id=event.job_id,
    )

    if not event.index_dates.works:
        raise ValueError("Destination index for works must be specified in the event.")

    objects_to_index = event.objects_to_index or _get_objects_to_index(event)

    es_client = utils.elasticsearch.get_serverless_client(es_mode)

    index_name = event.index_dates.works

    # Create index if mapping file provided
    if create_index_mapping:
        print(
            f"Creating index '{index_name}' with mapping from '{create_index_mapping}'..."
        )
        with open(create_index_mapping) as f:
            mapping = json.load(f)

        es_client.indices.create(index=index_name, body=mapping)

        # Wait for index to be ready (yellow = all primary shards allocated and ready for writes)
        print(f"Waiting for index '{index_name}' to be ready...")
        es_client.cluster.health(
            index=index_name, wait_for_status="yellow", timeout="30s"
        )
        print(f"Index '{index_name}' created and ready.")

    total_success_count = 0
    for s3_object in objects_to_index:
        indexable_data = get_indexable_data(event, s3_object.s3_uri)

        logger.info(
            "Loading documents to ES index",
            count=len(indexable_data),
            ingestor_type=event.ingestor_type,
            index_name=index_name,
        )

        # success_count, _ = elasticsearch.helpers.bulk(
        #     es_client, generate_operations(index_name, indexable_data)
        # )

        # logger.info("Successfully indexed documents", count=success_count)

        # total_success_count += success_count

    event_payload = event.model_dump(exclude={"objects_to_index"})

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
    parser.add_argument(
        "--ingestor-type",
        type=str,
        choices=["works"],
        help="The type of the records being ingested",
        required=False,
        default="works",
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="The pipeline that is being ingested to, will default to 'dev'.",
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--index-date-merged",
        type=str,
        help="The merged index to read from. Only for model parity, not used in prototype",
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--works-source",
        type=str,
        help='The S3 prefix where the source file(s) are located, e.g. "s3://bucket/prefix"',
        required=True,
    )
    parser.add_argument(
        "--works-destination-index",
        type=str,
        help="The index that is being ingested to.",
        required=True,
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
        "--create-index",
        type=str,
        help="Path to index mapping JSON file. If provided, creates the index before ingestion.",
        required=False,
    )

    args = parser.parse_args()
    base_event = IngestorStepEvent.from_argparser(args)

    event = IngestorIndexerLambdaEvent(**base_event.model_dump())
    handler(event, es_mode="public", create_index_mapping=args.create_index)


if __name__ == "__main__":
    parser: ArgumentParser = ArgumentParser()
    local_handler(parser)


# AWS_PROFILE=platform-developer uv run ingestor_indexer.py \
# --ingestor-type=works \
# --works-source="ingestor_works/dev/dev" \
# --works-destination-index="works-semantic-v1" \
# --load-format=jsonl

# optional:
# --create-index="path/to/mapping.json"
