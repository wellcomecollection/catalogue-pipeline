import argparse
import typing

import polars as pl

from ingestor.models.step_events import IngestorDeletionsLambdaEvent
from models.events import (
    IncrementalGraphRemoverEvent,
)
from removers.elasticsearch_remover import ElasticsearchRemover
from utils.aws import df_from_s3_parquet
from utils.elasticsearch import ElasticsearchMode
from utils.reporting import DeletionReport
from utils.safety import validate_fractional_change


def get_ids_to_delete(event: IngestorDeletionsLambdaEvent) -> set[str]:
    """Return a list of concept IDs marked for deletion from the ES index."""

    # Reconstruct the original incremental graph remover event which wrote the removed IDs to a parquet file
    remover_event = IncrementalGraphRemoverEvent(
        transformer_type="catalogue_concepts",
        entity_type="nodes",
        pipeline_date=event.pipeline_date,
        window=event.window,
    )

    # Retrieve a log of concept IDs which were deleted from the graph (see `graph_remover.py`).
    df = df_from_s3_parquet(remover_event.get_s3_uri("parquet", "deleted_ids"))

    ids = []
    if len(df) > 0:
        ids = pl.Series(df.select(pl.first())).to_list()
    return set(ids)


def handler(
    event: IngestorDeletionsLambdaEvent, es_mode: ElasticsearchMode = "private"
) -> None:
    es_remover = ElasticsearchRemover(event, es_mode)
    ids_to_delete = get_ids_to_delete(event)
    current_id_count = es_remover.get_document_count()

    # This is part of a safety mechanism. If two sets of IDs differ by more than the DEFAULT_THRESHOLD
    # (set to 5%), an exception will be raised.
    validate_fractional_change(
        modified_size=len(ids_to_delete),
        total_size=current_id_count,
        force_pass=event.force_pass,
    )
    deleted_count = es_remover.delete_documents(ids_to_delete)

    report = DeletionReport(**event.model_dump(), deleted_count=deleted_count)
    report.publish()


def lambda_handler(event: dict, context: typing.Any) -> None:
    handler(IngestorDeletionsLambdaEvent.model_validate(event))


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="The pipeline date corresponding to the concepts index to remove from.",
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help='The concepts index date that is being removed from, will default to "dev".',
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help="The job ID for the current run.",
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
        "--es-mode",
        type=str,
        help="Which ES instance to connect to. Use 'public' to connect to the production cluster.",
        required=False,
        choices=["local", "public"],
        default="local",
    )
    parser.add_argument(
        "--force-pass",
        type=bool,
        help="Whether to override a safety check which prevents document removal if the percentage of removed items is above a certain threshold.",
        default=False,
    )

    args = parser.parse_args()
    event = IngestorDeletionsLambdaEvent.from_argparser(args)
    handler(event, es_mode=args.es_mode)


if __name__ == "__main__":
    local_handler()
