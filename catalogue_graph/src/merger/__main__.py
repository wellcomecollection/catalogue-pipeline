"""Runs the batch merger locally, as its state machine does: matches, merges every
changed group, then replaces the baseline."""

import argparse

from merger.models.step_events import MatchEvent, MatchResult, MergeGroupEvent
from merger.steps import match, merge_group
from utils.argparse import add_pipeline_event_args
from utils.aws import delete_s3_object, df_from_s3_parquet, df_to_s3_parquet
from utils.elasticsearch import ElasticsearchMode
from utils.logger import ExecutionContext, get_trace_id
from utils.steps import create_job_id


def commit_components(result: MatchResult) -> None:
    """Replace the baseline with this run's components and delete the job's copy, as
    the state machine's last two states do once every group is merged."""
    df_to_s3_parquet(
        df_from_s3_parquet(result.job_components_s3_uri), result.components_s3_uri
    )
    delete_s3_object(result.job_components_s3_uri)


def group_events(event: MatchEvent, result: MatchResult) -> list[MergeGroupEvent]:
    """Each group's event, built from the run's event."""
    return [
        MergeGroupEvent.model_validate(
            {
                **event.model_dump(),
                "iceberg_snapshot_id": result.iceberg_snapshot_id,
                "group": group,
            }
        )
        for group in range(result.group_count)
    ]


def main() -> None:
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

    event = MatchEvent(
        pipeline_date=args.pipeline_date,
        graph_date=args.graph_date,
        job_id=args.job_id or create_job_id(),
        iceberg_snapshot_id=args.iceberg_snapshot_id,
    )
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="batch_merger",
    )
    result = match.handler(event, execution_context, args.use_rest_api_table)
    print(result.model_dump_json())
    if args.match_only:
        return

    es_mode: ElasticsearchMode = args.es_mode
    for group_event in group_events(event, result):
        group_result = merge_group.handler(
            group_event, execution_context, es_mode, args.use_rest_api_table
        )
        print(group_result.model_dump_json())
    commit_components(result)


if __name__ == "__main__":
    main()
