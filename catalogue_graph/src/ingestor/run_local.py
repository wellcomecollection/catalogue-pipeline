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

def write_loader_result_to_jsonl(loader_result: IngestorIndexerLambdaEvent, filename: str) -> None:
    import json
    objs = loader_result.objects_to_index or []
    with open(filename, "w") as f:
        for obj in objs:
            f.write(json.dumps(obj.model_dump()) + "\n")

def read_jsonl_and_index(filename: str, loader_event: IngestorLoaderLambdaEvent) -> None:
    import json
    from ingestor.models.step_events import IngestorIndexerObject, IngestorIndexerLambdaEvent
    objects = []
    with open(filename) as f:
        for line in f:
            obj = IngestorIndexerObject.model_validate(json.loads(line))
            objects.append(obj)
    event = IngestorIndexerLambdaEvent(**loader_event.model_dump(), objects_to_index=objects)
    run_index(event)


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
        "--index-date-merged",
        type=str,
        help="The merged index date to read from, will default to pipeline date.",
        required=False,
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help="The index date that is being ingested to, will default to 'dev'.",
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

    loader_result = loader_handler(loader_event, es_mode="public", load_destination="local")
    write_loader_result_to_jsonl(loader_result, "loader_result.jsonl")
    read_jsonl_and_index("loader_result.jsonl", loader_event)


if __name__ == "__main__":
    main()

# AWS_PROFILE=platform-developer python -m ingestor.run_local 
# --ingestor-type=works 
# --pipeline-date=2025-10-02 
# --index-date-merged=2025-10-02
# --index-date -> TBD serverless 
# --window-start
# --window-end
# --job-id
# --limit

# Fixed set of works
# Visible
# 