import argparse
from collections.abc import Iterable
from typing import Literal

BasePipelineEventArgument = Literal[
    "window",
    "pipeline_date",
    "index_date_merged",
    "pit_id",
    "environment",
    "es_mode",
]


def add_pipeline_event_args(
    parser: argparse.ArgumentParser, args: Iterable[BasePipelineEventArgument]
) -> None:
    """
    Add selected commonly used arguments to the given ArgumentParser
    so that they can be parsed into a BasePipelineEvent object.
    """
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
            help="Which pipeline date to use when connecting to ES and reading/writing S3 files. Will default to 'dev'.",
            required=False,
            default="dev",
        )
    if "index_date_merged" in args:
        parser.add_argument(
            "--index-date-merged",
            type=str,
            help="The merged index date to read from, will default to pipeline date.",
            required=False,
        )
    if "pit_id" in args:
        parser.add_argument(
            "--pit-id",
            type=str,
            help="An Elasticsearch point in time ID to use when extracting data from the merged index.",
            required=False,
        )
    if "environment" in args:
        parser.add_argument(
            "--environment",
            type=str,
            help="Which environment to connect to (used for Neptune and S3 bucket selection).",
            required=False,
            choices=["prod", "dev"],
            default="dev",
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


def validate_es_mode_for_writes(
    parser: argparse.ArgumentParser, args: argparse.Namespace
) -> None:
    """
    When running services which write to an Elasticsearch index locally, we disallow combining
    the production Elasticsearch cluster (`es_mode=public` or `es_mode=private`) with the development
    Neptune cluster (`environment=dev`) so that we cannot accidentally write non-production data to
    production indexes.
    """
    if args.environment == "dev" and args.es_mode != "local":
        parser.error(f"--es-mode={args.es_mode} cannot be used with --environment=dev")
