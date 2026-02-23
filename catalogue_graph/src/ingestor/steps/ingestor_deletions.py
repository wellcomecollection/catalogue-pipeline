import argparse
import typing

import polars as pl
import structlog

from ingestor.models.step_events import IngestorDeletionsLambdaEvent
from models.events import (
    IncrementalGraphRemoverEvent,
)
from removers.elasticsearch_remover import ElasticsearchRemover
from utils.argparse import (
    add_cluster_connection_args,
    add_pipeline_event_args,
    validate_cluster_connection_args,
)
from utils.aws import df_from_s3_parquet
from utils.elasticsearch import ElasticsearchMode
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.reporting import DeletionReport
from utils.safety import validate_fractional_change

logger = structlog.get_logger(__name__)


def get_ids_to_delete(event: IngestorDeletionsLambdaEvent) -> set[str]:
    """Return a list of concept IDs marked for deletion from the ES index."""

    # Reconstruct the original incremental graph remover event which wrote the removed IDs to a parquet file
    remover_event = IncrementalGraphRemoverEvent(
        transformer_type="catalogue_concepts",
        entity_type="nodes",
        pipeline_date=event.pipeline_date,
        window=event.window,
        environment=event.environment,
    )

    # Retrieve a log of concept IDs which were deleted from the graph (see `graph_remover.py`).
    df = df_from_s3_parquet(remover_event.get_s3_uri("parquet", "deleted_ids"))

    ids = []
    if len(df) > 0:
        ids = pl.Series(df.select(pl.first())).to_list()
    return set(ids)


def handler(
    event: IngestorDeletionsLambdaEvent,
    execution_context: ExecutionContext | None = None,
    es_mode: ElasticsearchMode = "private",
) -> None:
    setup_logging(execution_context)

    logger.info(
        "Received event",
        pipeline_date=event.pipeline_date,
        index_date=event.index_date,
    )

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
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="ingestor_deletions",
    )
    handler(IngestorDeletionsLambdaEvent.model_validate(event), execution_context)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    add_pipeline_event_args(parser, {"pipeline_date", "index_date_merged", "window"})
    add_cluster_connection_args(parser, {"es_mode", "environment"})

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
        "--force-pass",
        type=bool,
        help="Whether to override a safety check which prevents document removal if the percentage of removed items is above a certain threshold.",
        default=False,
    )

    args = parser.parse_args()
    validate_cluster_connection_args(parser, args)
    event = IngestorDeletionsLambdaEvent.from_argparser(args)
    handler(event, es_mode=args.es_mode)


if __name__ == "__main__":
    local_handler()
