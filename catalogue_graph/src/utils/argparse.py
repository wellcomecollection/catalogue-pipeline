import argparse
from collections.abc import Iterable
from typing import Literal

BasePipelineEventArgument = Literal[
    "window", "pipeline_date", "index_date_merged", "pit_id"
]
ClusterConnectionArgument = Literal["es_mode", "neptune_environment"]


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
            help="Which pipeline date to use. Will default to 'dev'.",
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


def add_cluster_connection_args(
    parser: argparse.ArgumentParser, args: Iterable[ClusterConnectionArgument]
) -> None:
    """
    Add selected commonly used arguments to the given ArgumentParser
    so local handlers can choose Elasticsearch and Neptune environments.
    """
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
