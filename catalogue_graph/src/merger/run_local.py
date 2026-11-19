"""Runs the batch merger's steps locally, in the order its state machine runs them in
AWS: match, merge every changed group, then replace the baseline."""

import argparse
import sys

from merger.models.step_events import (
    CommitEvent,
    MatchEvent,
    MatchResult,
    MergeGroupEvent,
)
from merger.steps import commit, match, merge_group
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
    """Why the combination of arguments is not allowed, if it is not. A match-only run
    writes neither documents nor a baseline, so any combination is allowed."""
    if args.match_only:
        return None
    local_table = not args.use_rest_api_table
    local_es = args.es_mode == "local"
    pipeline_baseline = args.pipeline_date != "dev"

    if local_table and not local_es:
        return "Works from the local table cannot be written to the public cluster."
    if local_table and pipeline_baseline:
        return (
            "A pipeline's baseline cannot be replaced from the local table, as the "
            "pipeline's own changes would then be skipped by its next run."
        )
    if local_es and pipeline_baseline:
        return (
            "A pipeline's baseline cannot be replaced after writing to a local "
            "Elasticsearch, as the merged works would never reach the pipeline's index."
        )
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
        default=create_job_id(include_seconds=True),
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

    event = MatchEvent.from_argparser(args)
    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="batch_merger",
    )
    result = match.handler(event, execution_context, args.use_rest_api_table)
    if args.match_only:
        return

    es_mode: ElasticsearchMode = args.es_mode
    for group_event in group_events(event, result):
        merge_group.handler(
            group_event, execution_context, es_mode, args.use_rest_api_table
        )
    commit_event = CommitEvent.model_validate(
        {**event.model_dump(), "group_count": result.group_count}
    )
    commit_result = commit.handler(commit_event, execution_context)
    if commit_result.failed_component_count:
        sys.exit(f"{commit_result.failed_component_count} components failed to merge")


if __name__ == "__main__":
    main()
