import argparse
import typing

import polars as pl
import structlog

from models.events import IncrementalGraphRemoverEvent
from removers.base_graph_remover_incremental import BaseGraphRemoverIncremental
from removers.catalogue_concepts_remover import CatalogueConceptsGraphRemover
from removers.catalogue_work_identifiers_remover import (
    CatalogueWorkIdentifiersGraphRemover,
)
from removers.catalogue_works_remover import CatalogueWorksGraphRemover
from utils.aws import (
    df_to_s3_parquet,
)
from utils.elasticsearch import ElasticsearchMode
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.reporting import IncrementalGraphRemoverReport
from utils.types import CatalogueTransformerType, EntityType

logger = structlog.get_logger(__name__)


def get_remover(
    event: IncrementalGraphRemoverEvent, is_local: bool
) -> BaseGraphRemoverIncremental:
    es_mode: ElasticsearchMode = "public" if is_local else "private"

    if event.transformer_type == "catalogue_works":
        return CatalogueWorksGraphRemover(event, es_mode)
    if event.transformer_type == "catalogue_concepts":
        return CatalogueConceptsGraphRemover(event, es_mode)
    if event.transformer_type == "catalogue_work_identifiers":
        return CatalogueWorkIdentifiersGraphRemover(event, es_mode)

    raise ValueError(f"Unknown remover type: '{event.transformer_type}'")


def handler(
    event: IncrementalGraphRemoverEvent,
    execution_context: ExecutionContext,
    is_local: bool = False,
) -> None:
    setup_logging(execution_context)

    remover = get_remover(event, is_local)
    deleted_ids = remover.remove(event.force_pass)

    # Write removed IDs to a parquet file
    s3_file_uri = event.get_s3_uri("parquet", "deleted_ids")
    df_to_s3_parquet(pl.DataFrame(deleted_ids), s3_file_uri)

    report = IncrementalGraphRemoverReport(
        **event.model_dump(), deleted_count=len(deleted_ids)
    )
    report.publish()

    logger.info(
        "List of deleted IDs saved",
        s3_uri=s3_file_uri,
        deleted_count=len(deleted_ids),
    )


def lambda_handler(event: dict, context: typing.Any) -> None:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="graph_remover_incremental",
    )
    handler(IncrementalGraphRemoverEvent.model_validate(event), execution_context)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--transformer-type",
        type=str,
        choices=typing.get_args(CatalogueTransformerType),
        help="Which incremental remover to run.",
        required=True,
    )
    parser.add_argument(
        "--entity-type",
        type=str,
        choices=typing.get_args(EntityType),
        help="Which entity type to remove using the specified remover (nodes or edges).",
        required=True,
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help="The pipeline date associated with the removed items.",
        required=True,
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

    args = parser.parse_args()
    event = IncrementalGraphRemoverEvent.from_argparser(args)

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="graph_remover_incremental",
    )
    handler(event, execution_context, is_local=True)


if __name__ == "__main__":
    local_handler()
