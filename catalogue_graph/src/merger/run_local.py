"""Runs the batch merger's steps locally, in the order its state machine runs them in
AWS: match, merge every changed group, then replace the baseline."""

import argparse

from merger.models.step_events import (
    CommitEvent,
    MatchEvent,
    MatchResult,
    MergeGroupEvent,
)
from merger.steps import commit, match, merge_group
from models.pipeline_scope import PipelineIndexDates
from utils.argparse import add_pipeline_event_args
from utils.elasticsearch import ElasticsearchMode
from utils.logger import ExecutionContext, get_trace_id
from utils.steps import create_job_id


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


def environment_error(args: argparse.Namespace) -> str | None:
    """Why the arguments mix environments, if they do. A run reads one table, writes one
    cluster and replaces one baseline, and all three must belong to the same environment,
    or the baseline would record merges that were written somewhere else."""
    if args.match_only:
        return None
    if args.use_rest_api_table:
        if args.es_mode == "local":
            return "--use-rest-api-table needs --es-mode public, to write to that pipeline's cluster"
        if not (args.index_date_merged and args.index_date_initial):
            return "--use-rest-api-table needs --index-date-merged and --index-date-initial, the dates of the batch merger's indices"
        return None
    if args.es_mode != "local":
        return "--es-mode public needs --use-rest-api-table, to merge that pipeline's works"
    if args.pipeline_date != "dev" or args.graph_date != "dev":
        return "the local table needs --pipeline-date dev and --graph-date dev, to keep the baseline local"
    return None


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Merge the components that changed since the last run."
    )
    add_pipeline_event_args(
        parser,
        {
            "pipeline_date",
            "graph_date",
            "es_mode",
            "index_date_merged",
            "index_date_initial",
        },
    )
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
    if error := environment_error(args):
        parser.error(error)

    event = MatchEvent(
        pipeline_date=args.pipeline_date,
        graph_date=args.graph_date,
        index_dates=PipelineIndexDates(
            merged=args.index_date_merged, initial=args.index_date_initial
        ),
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
    commit_event = CommitEvent.model_validate(
        {**event.model_dump(), "group_count": result.group_count}
    )
    print(commit.handler(commit_event, execution_context).model_dump_json())


if __name__ == "__main__":
    main()
