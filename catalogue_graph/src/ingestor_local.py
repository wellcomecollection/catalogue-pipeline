#!/usr/bin/env python

import argparse

from ingestor_indexer import (
    IngestorIndexerConfig,
)
from ingestor_indexer import (
    handler as indexer_handler,
)
from ingestor_loader import (
    IngestorLoaderConfig,
)
from ingestor_loader import (
    handler as loader_handler,
)
from ingestor_trigger import (
    IngestorTriggerConfig,
    IngestorTriggerLambdaEvent,
)
from ingestor_trigger import (
    handler as trigger_handler,
)


# Run the whole pipeline locally, Usage: python src/ingestor_local.py --pipeline-date 2021-07-01 --job-id 123
def main() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--job-id",
        type=str,
        help="The ID of the job to process, will use a default based on the current timestamp if not provided.",
        required=False,
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="The date to use for the pipeline, required.",
        required=False,
    )
    parser.add_argument(
        "--limit",
        type=int,
        help="The number of shards to process, will process all if not specified.",
        required=False,
    )

    args = parser.parse_args()

    trigger_event = IngestorTriggerLambdaEvent(**args.__dict__)
    config = IngestorTriggerConfig(is_local=True)
    trigger_result = trigger_handler(trigger_event, config)

    trigger_result = trigger_result[: args.limit] if args.limit else trigger_result

    loader_config = IngestorLoaderConfig(is_local=True)
    loader_results = [loader_handler(e, loader_config) for e in trigger_result]

    indexer_config = IngestorIndexerConfig(is_local=True)
    success_counts = [indexer_handler(e, indexer_config) for e in loader_results]

    total_success_count = sum(success_counts)
    print(f"Indexed {total_success_count} documents.")


if __name__ == "__main__":
    main()
