#!/usr/bin/env python

import argparse
import typing

import structlog

from ingestor.models.step_events import (
    IngestorLoaderLambdaEvent,
)
from ingestor.steps.ingestor_indexer import handler as indexer_handler
from ingestor.steps.ingestor_loader import create_job_id
from ingestor.steps.ingestor_loader import handler as loader_handler
from utils.argparse import add_pipeline_event_args, validate_es_mode_for_writes
from utils.types import IngestorType

logger = structlog.get_logger(__name__)


# Run the whole pipeline locally.
# Usage: AWS_PROFILE=platform-developer uv run src/ingestor/run_local.py --ingestor-type=concepts
# Alternative usage: AWS_PROFILE=platform-developer python -m ingestor.run_local --ingestor-type=concepts --pipeline-date=2025-05-01
def main() -> None:
    parser = argparse.ArgumentParser(description="")
    add_pipeline_event_args(
        parser,
        {
            "pipeline_date",
            "index_date_merged",
            "index_date_augmented",
            "window",
            "ids",
            "environment",
            "es_mode",
        },
    )
    parser.add_argument(
        "--ingestor-type",
        type=str,
        choices=typing.get_args(IngestorType),
        help="Which ingestor to run (works, concepts, images).",
        required=True,
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help="The index date that is being ingested to, will default to 'dev'.",
        required=False,
        default="dev",
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
    validate_es_mode_for_writes(parser, args)
    loader_event = IngestorLoaderLambdaEvent.from_argparser(args)
    es_mode = args.es_mode

    loader_result = loader_handler(
        loader_event,
        es_mode=es_mode,
    )
    result = indexer_handler(loader_result, es_mode=es_mode)
    logger.info("Indexed documents", count=result.success_count)


if __name__ == "__main__":
    main()
