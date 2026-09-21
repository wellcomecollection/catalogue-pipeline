#!/usr/bin/env python
import argparse
import typing

import structlog

from merger.components import find_changed_components, read_previous_components
from merger.config import get_works_identified_table
from merger.matcher import load_works, match_works
from merger.models.step_events import MergerEvent, MergerResult
from utils.argparse import add_pipeline_event_args
from utils.aws import df_to_s3_parquet
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.steps import create_job_id

logger = structlog.get_logger(__name__)


def handler(
    event: MergerEvent,
    execution_context: ExecutionContext | None = None,
    use_rest_api_table: bool = True,
) -> MergerResult:
    setup_logging(execution_context)

    table = get_works_identified_table(use_rest_api_table)
    snapshot = table.current_snapshot()
    if snapshot is None:
        raise ValueError("The works identified table is empty")
    snapshot_id = event.iceberg_snapshot_id or snapshot.snapshot_id

    works = load_works(table, snapshot_id)
    current = match_works(works)
    previous = read_previous_components(event.components_s3_uri)
    changed = find_changed_components(current, previous)

    # Merging the changed components and updating the stored components follow once
    # the merge rules are ported.
    changed_components_s3_uri = event.get_s3_uri("changed_components")
    df_to_s3_parquet(changed, changed_components_s3_uri)

    return MergerResult(
        job_id=event.job_id,
        iceberg_snapshot_id=snapshot_id,
        work_count=works.height,
        changed_work_count=changed.height,
        changed_component_count=changed["component_id"].n_unique(),
        changed_components_s3_uri=changed_components_s3_uri,
    )


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="merger",
    )
    if "job_id" not in event:
        event["job_id"] = create_job_id()

    return handler(MergerEvent.model_validate(event), execution_context).model_dump(
        mode="json"
    )


def local_handler() -> None:
    parser = argparse.ArgumentParser(
        description="Find the merge components that changed since the last run."
    )
    add_pipeline_event_args(parser, {"pipeline_date", "graph_date"})
    parser.add_argument(
        "--job-id",
        type=str,
        required=False,
        help="Optional job id (defaults to the current time).",
    )
    parser.add_argument(
        "--iceberg-snapshot-id",
        type=int,
        required=False,
        help="Read the works identified table at this snapshot instead of the current one.",
    )
    parser.add_argument(
        "--use-rest-api-table",
        action="store_true",
        help="Read the S3 Tables works identified table instead of the local one.",
    )
    args = parser.parse_args()

    event = MergerEvent(
        pipeline_date=args.pipeline_date,
        graph_date=args.graph_date,
        job_id=args.job_id or create_job_id(),
        iceberg_snapshot_id=args.iceberg_snapshot_id,
    )
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="merger",
    )
    result = handler(event, execution_context, args.use_rest_api_table)
    print(result.model_dump_json())


if __name__ == "__main__":
    local_handler()
