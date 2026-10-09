#!/usr/bin/env python
"""
Find the works to re-ingest because a concept they display was relabelled.

Work documents hold a copy of the concept's label and nothing marks a work as changed when its
concept changes, so a corrected label never reaches the works index on its own. Comparing the
last two full concepts ingests catches a relabelling whatever caused it.
See wellcomecollection/platform#6764.
"""

import argparse
import typing
from pathlib import PurePosixPath
from typing import cast

import boto3
import structlog

import config
from clients.neptune_client import NeptuneClient
from ingestor.models.step_events import (
    IngestorRelabelledWorksLambdaEvent,
    RelabelledWorks,
)
from models.events import PipelineIndexDates
from utils.argparse import add_pipeline_event_args
from utils.aws import table_from_s3_parquet
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.reporting import RelabelledWorksReport

logger = structlog.get_logger(__name__)

JOB_PREFIX = "job-"

# Works copy the display label, so that is the field a work goes stale against.
ID_COLUMN = "query.id"
LABEL_COLUMN = "display.displayLabel"


def _paginate_keys(bucket: str, prefix: str) -> typing.Iterator[str]:
    paginator = boto3.client("s3").get_paginator("list_objects_v2")
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
        for s3_object in page.get("Contents", []):
            yield s3_object["Key"]


def _list_job_names(bucket: str, prefix: str) -> list[str]:
    """Return the name of every full concepts ingest under the prefix, oldest first."""
    job_names = set()
    for key in _paginate_keys(bucket, f"{prefix}/"):
        folder = key[len(prefix) :].lstrip("/").split("/")[0]
        if folder.startswith(JOB_PREFIX):
            job_names.add(folder)

    # Job names carry a timestamp, so sorting them as strings sorts them by time.
    return sorted(job_names)


def _read_labels(bucket: str, job_prefix: str) -> dict[str, str]:
    """Return the display label of every concept written by one ingest job."""
    labels: dict[str, str] = {}
    for key in _paginate_keys(bucket, f"{job_prefix}/"):
        if not key.endswith(".parquet"):
            continue

        table = table_from_s3_parquet(f"s3://{bucket}/{key}", [ID_COLUMN, LABEL_COLUMN])
        ids = cast(list[str], table.column(ID_COLUMN.split(".")[-1]).to_pylist())
        job_labels = cast(
            list[str], table.column(LABEL_COLUMN.split(".")[-1]).to_pylist()
        )
        labels.update(zip(ids, job_labels, strict=True))

    return labels


def get_relabelled_concept_ids(
    event: IngestorRelabelledWorksLambdaEvent,
) -> tuple[str | None, list[str]]:
    """Return the latest full ingest job and the concepts whose label changed since the one before."""
    bucket = config.CATALOGUE_GRAPH_S3_BUCKET
    prefix = str(PurePosixPath(*event.s3_prefix_parts))

    job_names = _list_job_names(bucket, prefix)
    if len(job_names) < 2:
        logger.info("Too few full concepts ingests to compare", count=len(job_names))
        return (job_names[-1] if job_names else None), []

    previous, latest = job_names[-2], job_names[-1]
    logger.info("Comparing full concepts ingests", previous=previous, latest=latest)

    before = _read_labels(bucket, f"{prefix}/{previous}")
    after = _read_labels(bucket, f"{prefix}/{latest}")

    # A concept absent from the previous job is new, and the work which introduced it was
    # ingested along with it, so only a changed label leaves a work stale.
    relabelled = [
        concept_id
        for concept_id, label in after.items()
        if concept_id in before and before[concept_id] != label
    ]

    logger.info(
        "Compared concept labels",
        previous_count=len(before),
        latest_count=len(after),
        relabelled_count=len(relabelled),
    )
    return latest, sorted(relabelled)


def get_work_ids(neptune_client: NeptuneClient, concept_ids: list[str]) -> list[str]:
    """Return the ids of every work which displays one of the given concepts."""
    works_by_concept = neptune_client.get_source_node_ids(
        concept_ids,
        edge_label="HAS_CONCEPT",
        node_label="Concept",
        source_label="Work",
    )

    work_ids: set[str] = set()
    for ids in works_by_concept.values():
        work_ids |= ids

    return sorted(work_ids)


def handler(
    event: IngestorRelabelledWorksLambdaEvent,
    execution_context: ExecutionContext | None = None,
) -> RelabelledWorks:
    setup_logging(execution_context)

    logger.info(
        "Received event",
        pipeline_date=event.pipeline_date,
        graph_date=event.graph_date,
        index_date=event.index_date,
    )

    latest_job, concept_ids = get_relabelled_concept_ids(event)

    work_ids = []
    if concept_ids and len(concept_ids) <= event.max_work_ids:
        work_ids = get_work_ids(NeptuneClient(event.graph_date), concept_ids)

    over_limit = max(len(concept_ids), len(work_ids)) > event.max_work_ids
    if over_limit:
        logger.warning(
            "Too many relabelled concepts to refresh their works by id",
            concept_count=len(concept_ids),
            work_count=len(work_ids),
            max_work_ids=event.max_work_ids,
        )

    report_event = event.model_copy(update={"job_id": latest_job or event.job_id})
    report = RelabelledWorksReport(
        **report_event.model_dump(exclude={"max_work_ids"}),
        concept_count=len(concept_ids),
        work_count=0 if over_limit else len(work_ids),
        over_limit=over_limit,
    )
    report.publish()

    return RelabelledWorks(
        concept_count=len(concept_ids),
        work_count=0 if over_limit else len(work_ids),
        work_ids=[] if over_limit else work_ids,
        over_limit=over_limit,
    )


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="ingestor_relabelled_works",
    )
    validated = IngestorRelabelledWorksLambdaEvent.model_validate(event)
    return handler(validated, execution_context).model_dump(mode="json")


def local_handler() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    add_pipeline_event_args(parser, {"pipeline_date", "graph_date"})
    parser.add_argument(
        "--index-date",
        type=str,
        help="The concepts index date whose ingest jobs are compared, will default to 'dev'.",
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--max-work-ids",
        type=int,
        help="Report and stop instead of returning work IDs above this many.",
        required=False,
        default=5000,
    )

    args = parser.parse_args()
    event = IngestorRelabelledWorksLambdaEvent(
        pipeline_date=args.pipeline_date,
        graph_date=args.graph_date,
        index_dates=PipelineIndexDates(concepts=args.index_date),
        max_work_ids=args.max_work_ids,
    )
    print(handler(event).model_dump_json(indent=2))


if __name__ == "__main__":
    local_handler()
