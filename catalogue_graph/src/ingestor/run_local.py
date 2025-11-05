#!/usr/bin/env python

import argparse
import typing

from ingestor.models.step_events import (
    IngestorIndexerLambdaEvent,
    IngestorLoaderLambdaEvent,
)
from ingestor.steps.ingestor_indexer import handler as indexer_handler
from ingestor.steps.ingestor_loader import create_job_id
from ingestor.steps.ingestor_loader import handler as loader_handler
from utils.types import IngestorType


def run_index(loader_result: IngestorIndexerLambdaEvent) -> None:
    result = indexer_handler(loader_result, es_mode="public")
    print(f"Indexed {result.success_count} documents.")


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
        "--window-start",
        type=str,
        help="Start of the processed window (e.g. 2025-01-01T00:00). Incremental mode only.",
        required=False,
    )
    parser.add_argument(
        "--window-end",
        type=str,
        help="End of the processed window (e.g. 2025-01-01T00:00). Incremental mode only.",
        required=False,
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="The ID of the job to process, will use a default based on the current timestamp if not provided.",
        required=False,
        default=create_job_id(),
    )
    parser.add_argument(
        "--limit",
        type=int,
        help="The number of shards to process, will process all if not specified.",
        required=False,
    )

    args = parser.parse_args()
    loader_event = IngestorLoaderLambdaEvent.from_argparser(args)

    loader_result = loader_handler(loader_event, es_mode="public")
    run_index(loader_result)


if __name__ == "__main__":
    main()
