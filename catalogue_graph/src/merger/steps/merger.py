#!/usr/bin/env python
import argparse
import typing
from datetime import UTC, datetime

import polars as pl
import structlog
from pyiceberg.table import Table as IcebergTable

from id_minter.iceberg import get_works_identified_table
from merger.components import (
    COMPONENTS_SCHEMA,
    assign_groups,
    find_changed_components,
    read_previous_components,
)
from merger.matcher import load_works, match_works
from merger.models.step_events import MergeGroupEvent, MergerEvent, MergerResult
from merger.steps import merge
from utils.argparse import add_pipeline_event_args
from utils.aws import delete_s3_object, df_from_s3_parquet, df_to_s3_parquet
from utils.elasticsearch import ElasticsearchMode
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
    snapshot_id = event.iceberg_snapshot_id or (
        snapshot.snapshot_id if snapshot else None
    )

    if snapshot_id is None:
        # A new pipeline's table has no snapshot until the id minter first writes.
        logger.info("The works identified table is empty")
        work_count = 0
        current = pl.DataFrame(schema=COMPONENTS_SCHEMA)
    else:
        works = load_works(table, snapshot_id)
        work_count = works.height
        current = match_works(works)
    previous = read_previous_components(event.components_s3_uri)
    changed = assign_groups(find_changed_components(current, previous))

    changed_components_s3_uri = event.get_s3_uri("changed_components")
    df_to_s3_parquet(changed, changed_components_s3_uri)
    # The baseline is replaced with these once every group is merged.
    job_components_s3_uri = event.get_s3_uri("components")
    df_to_s3_parquet(current, job_components_s3_uri)

    merge_groups = []
    if snapshot_id is not None:
        merged_time = _snapshot_time(table, snapshot_id)
        merge_groups = [
            MergeGroupEvent(
                **event.model_dump(exclude={"iceberg_snapshot_id"}),
                iceberg_snapshot_id=snapshot_id,
                group=group,
                merged_time=merged_time,
            )
            for group in sorted(changed["group"].unique())
        ]

    return MergerResult(
        job_id=event.job_id,
        iceberg_snapshot_id=snapshot_id,
        work_count=work_count,
        changed_work_count=changed.height,
        changed_component_count=changed["component_id"].n_unique(),
        changed_components_s3_uri=changed_components_s3_uri,
        job_components_s3_uri=job_components_s3_uri,
        components_s3_uri=event.components_s3_uri,
        merge_groups=merge_groups,
    )


def commit_components(result: MergerResult) -> None:
    """Replace the baseline with this run's components and delete the job's copy, as
    the state machine's last two states do once every group is merged."""
    df_to_s3_parquet(
        df_from_s3_parquet(result.job_components_s3_uri), result.components_s3_uri
    )
    delete_s3_object(result.job_components_s3_uri)


def _snapshot_time(table: IcebergTable, snapshot_id: int) -> datetime:
    snapshot = table.snapshot_by_id(snapshot_id)
    if snapshot is None:
        raise ValueError(f"Snapshot {snapshot_id} not found")
    return datetime.fromtimestamp(snapshot.timestamp_ms / 1000, UTC)


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
        description="Merge the components that changed since the last run."
    )
    add_pipeline_event_args(parser, {"pipeline_date", "graph_date", "es_mode"})
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
    parser.add_argument(
        "--match-only",
        action="store_true",
        help="Find the changed components without merging them or replacing the baseline.",
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
    print(result.model_dump_json(exclude={"merge_groups"}))
    if args.match_only:
        return

    es_mode: ElasticsearchMode = args.es_mode
    for group_event in result.merge_groups:
        group_result = merge.handler(
            group_event, execution_context, es_mode, args.use_rest_api_table
        )
        print(group_result.model_dump_json())
    commit_components(result)


if __name__ == "__main__":
    local_handler()
