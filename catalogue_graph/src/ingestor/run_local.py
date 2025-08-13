#!/usr/bin/env python

import argparse
import typing

from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorLoaderMonitorLambdaEvent,
    IngestorTriggerLambdaEvent,
    IngestorTriggerMonitorLambdaEvent,
)
from ingestor.steps.ingestor_indexer import IngestorIndexerConfig
from ingestor.steps.ingestor_indexer import handler as indexer_handler
from ingestor.steps.ingestor_loader import IngestorLoaderConfig
from ingestor.steps.ingestor_loader import handler as loader_handler
from ingestor.steps.ingestor_loader_monitor import IngestorLoaderMonitorConfig
from ingestor.steps.ingestor_loader_monitor import handler as loader_monitor_handler
from ingestor.steps.ingestor_trigger import IngestorTriggerConfig
from ingestor.steps.ingestor_trigger import handler as trigger_handler
from ingestor.steps.ingestor_trigger_monitor import IngestorTriggerMonitorConfig
from ingestor.steps.ingestor_trigger_monitor import handler as trigger_monitor_handler
from utils.types import IngestorType


def run_trigger(
    event: IngestorTriggerLambdaEvent, args: argparse.Namespace
) -> IngestorTriggerMonitorLambdaEvent:
    config = IngestorTriggerConfig(is_local=True)
    trigger_result = trigger_handler(event, config)
    trigger_result.force_pass = args.force_pass

    if args.monitoring:
        trigger_monitor_handler(trigger_result, IngestorTriggerMonitorConfig())

    return trigger_result


def run_load(
    trigger_result: IngestorTriggerMonitorLambdaEvent, args: argparse.Namespace
) -> list[IngestorIndexerLambdaEvent]:
    loader_config = IngestorLoaderConfig(is_local=True)
    limit = args.limit or len(trigger_result.events)
    loader_results = [
        loader_handler(e, loader_config) for e in trigger_result.events[:limit]
    ]

    if args.monitoring:
        loader_monitor_event = IngestorLoaderMonitorLambdaEvent(
            **loader_results[0].model_dump(),
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
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help='The pipeline that is being ingested to, will default to "None".',
        required=False,
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help="The concepts index date that is being ingested to, will default to None.",
        required=False,
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

    trigger_event = IngestorTriggerLambdaEvent(**args.__dict__)

    trigger_result = run_trigger(trigger_event, args)
    loader_results = run_load(trigger_result, args)
    run_index(loader_results)


if __name__ == "__main__":
    main()
