import argparse
from collections.abc import Iterable
from typing import Literal

PipelineEventArgument = Literal[
    "window", "pipeline_date", "pit_id", "es_mode", "neptune_environment"
]


def add_pipeline_event_args(
    parser: argparse.ArgumentParser, args: Iterable[PipelineEventArgument]
) -> None:
    if "window" in args:
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
    if "pipeline_date" in args:
        parser.add_argument(
            "--pipeline-date",
            type=str,
            help="Which pipeline date to use. Will default to 'dev'.",
            required=False,
            default="dev",
        )
    if "pit_id" in args:
        parser.add_argument(
            "--pit-id",
            type=str,
            help="An Elasticsearch point in time ID to use when extracting data from the merged index.",
            required=False,
        )
    if "es_mode" in args:
        parser.add_argument(
            "--es-mode",
            type=str,
            help="Which Elasticsearch cluster to connect to. Use 'public' to connect to the production cluster.",
            required=False,
            choices=["local", "public"],
            default="local",
        )
    if "neptune_environment" in args:
        parser.add_argument(
            "--neptune-environment",
            type=str,
            help="Which Neptune cluster to connect to. Will default to 'dev'.",
            required=False,
            choices=["prod", "dev"],
            default="dev",
        )
