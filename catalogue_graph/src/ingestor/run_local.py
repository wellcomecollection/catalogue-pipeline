#!/usr/bin/env python

import argparse
import typing

from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorLoaderMonitorLambdaEvent,
)
from ingestor.steps.ingestor_indexer import IngestorIndexerConfig
from ingestor.steps.ingestor_indexer import handler as indexer_handler
from ingestor.steps.ingestor_loader import handler as loader_handler
from ingestor.steps.ingestor_loader_monitor import IngestorLoaderMonitorConfig
from ingestor.steps.ingestor_loader_monitor import handler as loader_monitor_handler
from ingestor.steps.ingestor_trigger import create_job_id
from utils.types import IngestorType


def run_load(
    loader_event: IngestorLoaderLambdaEvent, args: argparse.Namespace
) -> list[IngestorIndexerLambdaEvent]:
    loader_result = loader_handler(loader_event, is_local=True)

    if args.monitoring:
        loader_monitor_event = IngestorLoaderMonitorLambdaEvent(
            **loader_result.model_dump(),
            events=loader_results,
            force_pass=args.force_pass,
        )
        loader_monitor_handler(loader_monitor_event, IngestorLoaderMonitorConfig())

    return loader_results


def run_index(loader_results: list[IngestorIndexerLambdaEvent]) -> None:
    indexer_config = IngestorIndexerConfig(is_local=True)
    indexer_handler_responses = [
        indexer_handler(e, indexer_config) for e in loader_results
    ]

    total_success_count = sum(res.success_count for res in indexer_handler_responses)
    print(f"Indexed {total_success_count} documents.")


# Run the whole pipeline locally.
# Usage: AWS_PROFILE=platform-developer uv run src/ingestor/run_local.py --ingestor-type=concepts
# Alternative usage: AWS_PROFILE=platform-developer python -m ingestor.run_local --ingestor-type=concepts --pipeline-date=2025-05-01
def main() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--ingestor-type",
        type=str,
        choices=typing.get_args(IngestorType),
        help="Which ingestor to run (works or concepts).",
        required=True,
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="The ID of the job to process, will use a default based on the current timestamp if not provided.",
        required=False,
        default=create_job_id(),
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="The pipeline that is being ingested to, will default to 'dev'.",
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help="The concepts index date that is being ingested to, will default to 'dev'.",
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--limit",
        type=int,
        help="The number of shards to process, will process all if not specified.",
        required=False,
    )
    parser.add_argument(
        "--monitoring",
        action=argparse.BooleanOptionalAction,
        help="Whether to enable monitoring, will default to False.",
        default=False,
    )
    parser.add_argument(
        "--force-pass",
        action=argparse.BooleanOptionalAction,
        help="Whether to force pass monitoring checks, will default to False.",
        default=False,
    )

    args = parser.parse_args()

    loader_results = run_load(trigger_result, args)
    run_index(loader_results)


if __name__ == "__main__":
    main()
