"""Replaces the components baseline with the run's components once every group is
merged. Components that failed are left out, so the next run sees them as changed and
merges them again."""

import typing

import polars as pl
import structlog

from merger.models.step_events import CommitEvent, CommitResult
from utils.aws import delete_s3_object, df_from_s3_parquet, df_to_s3_parquet
from utils.logger import ExecutionContext, get_trace_id, setup_logging

logger = structlog.get_logger(__name__)


def handler(
    event: CommitEvent, execution_context: ExecutionContext | None = None
) -> CommitResult:
    setup_logging(execution_context)

    failed: list[str] = []
    for group in range(event.group_count):
        frame = df_from_s3_parquet(event.failed_components_s3_uri(group))
        failed += frame["component_id"].to_list()
    components = df_from_s3_parquet(event.get_s3_uri("components"))
    df_to_s3_parquet(
        components.filter(~pl.col("component_id").is_in(failed)),
        event.components_s3_uri,
    )
    # Every run writes a copy of the whole graph, which nothing reads once it is the
    # baseline, and the bucket has no lifecycle rule to expire them. The run's small
    # changed_components.parquet stays, as a record of what it merged.
    delete_s3_object(event.get_s3_uri("components"))

    logger.info("Committed components", failed_components=len(failed))
    return CommitResult(failed_component_count=len(failed))


def lambda_handler(event: dict, context: typing.Any) -> dict:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="batch_merger_commit",
    )
    return handler(CommitEvent.model_validate(event), execution_context).model_dump(
        mode="json"
    )
