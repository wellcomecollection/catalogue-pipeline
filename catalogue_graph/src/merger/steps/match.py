"""Finds the components that changed since the last run and splits them into groups."""

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
from merger.models.step_events import MatchEvent, MatchResult, MergeGroupEvent
from utils.aws import df_to_s3_parquet
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.steps import create_job_id

logger = structlog.get_logger(__name__)


def handler(
    event: MatchEvent,
    execution_context: ExecutionContext | None = None,
    use_rest_api_table: bool = True,
) -> MatchResult:
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

    return MatchResult(
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


def _snapshot_time(table: IcebergTable, snapshot_id: int) -> datetime:
    snapshot = table.snapshot_by_id(snapshot_id)
    if snapshot is None:
        raise ValueError(f"Snapshot {snapshot_id} not found")
    return datetime.fromtimestamp(snapshot.timestamp_ms / 1000, UTC)


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="batch_merger_match",
    )
    if "job_id" not in event:
        event["job_id"] = create_job_id()

    return handler(MatchEvent.model_validate(event), execution_context).model_dump(
        mode="json"
    )
