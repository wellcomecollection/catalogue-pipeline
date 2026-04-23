#!/usr/bin/env python
# Run extractor → bulk loader → bulk load poller locally as a single pipeline.
# Usage: AWS_PROFILE=platform-developer uv run graph/run_local.py --transformer-type=loc_concepts --entity-type=nodes

import argparse
import time
import typing

import structlog

from graph.steps.bulk_load_poller import handler as poller_handler
from graph.steps.bulk_loader import handler as bulk_loader_handler
from graph.steps.extractor import handler as extractor_handler
from models.events import (
    BulkLoaderEvent,
    ExtractorEvent,
)
from utils.argparse import add_pipeline_event_args
from utils.logger import ExecutionContext, get_trace_id
from utils.types import EntityType, TransformerType

logger = structlog.get_logger(__name__)

POLL_INTERVAL_SECONDS = 10


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run the full graph pipeline locally: extractor → bulk loader → bulk load poller."
    )
    add_pipeline_event_args(
        parser,
        {
            "pipeline_date",
            "index_date_merged",
            "index_date_augmented",
            "window",
            "ids",
            "pit_id",
            "environment",
            "es_mode",
        },
    )
    parser.add_argument(
        "--transformer-type",
        type=str,
        choices=typing.get_args(TransformerType),
        help="Which transformer to use for streaming.",
        required=True,
    )
    parser.add_argument(
        "--entity-type",
        type=str,
        choices=typing.get_args(EntityType),
        help="Which entity type to transform using the specified transformer (nodes or edges).",
        required=True,
    )
    args = parser.parse_args()

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="run_local",
    )

    logger.info("=== Step 1/3: Extractor ===")
    extractor_event = ExtractorEvent.from_argparser(args)
    extractor_handler(extractor_event, execution_context, es_mode=args.es_mode)

    logger.info("=== Step 2/3: Bulk loader ===")
    bulk_loader_event = BulkLoaderEvent.from_argparser(args)
    poller_event = bulk_loader_handler(bulk_loader_event, execution_context)

    logger.info("=== Step 3/3: Bulk load poller ===")
    while True:
        result = poller_handler(poller_event, execution_context)
        if result.status == "SUCCEEDED":
            break
        logger.info("Bulk load in progress...", load_id=poller_event.load_id)
        time.sleep(POLL_INTERVAL_SECONDS)


if __name__ == "__main__":
    main()
