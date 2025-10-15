import argparse
import typing
from pathlib import PurePosixPath

import polars as pl

import config
from models.events import IncrementalRemoverEvent
from removers.base_remover import BaseGraphRemover
from removers.catalogue_concepts_remover import CatalogueConceptsGraphRemover
from removers.catalogue_work_identifiers_remover import (
    CatalogueWorkIdentifiersGraphRemover,
)
from removers.catalogue_works_remover import CatalogueWorksGraphRemover
from utils.aws import (
    df_to_s3_parquet,
)
from utils.elasticsearch import ElasticsearchMode
from utils.types import EntityType, IncrementalGraphRemoverType


def get_s3_uri(event: IncrementalRemoverEvent) -> str:
    parts: list[str] = [config.INCREMENTAL_GRAPH_REMOVER_S3_PREFIX, event.pipeline_date]

    if event.window is not None:
        parts += ["windows", event.window.to_formatted_string()]

    prefix = PurePosixPath(*parts)

    return (
        f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/{prefix}/{event.remover_type}.parquet"
    )


def get_remover(event: IncrementalRemoverEvent, is_local: bool) -> BaseGraphRemover:
    es_mode: ElasticsearchMode = "public" if is_local else "private"

    if event.remover_type == "works":
        return CatalogueWorksGraphRemover(event, es_mode)
    if event.remover_type == "concepts":
        return CatalogueConceptsGraphRemover(event, es_mode)
    if event.remover_type == "work_identifiers":
        return CatalogueWorkIdentifiersGraphRemover(event, es_mode)

    raise ValueError(f"Unknown remover type: '{event.remover_type}'")


def handler(event: IncrementalRemoverEvent, is_local: bool = False) -> None:
    remover = get_remover(event, is_local)
    deleted_ids = remover.remove()

    # Write removed IDs to a parquet file
    s3_file_uri = get_s3_uri(event)
    df = pl.DataFrame(deleted_ids)
    df_to_s3_parquet(df, s3_file_uri)

    print(f"List of deleted IDs saved to {s3_file_uri}.")


def lambda_handler(event: dict, context: typing.Any) -> None:
    handler(IncrementalRemoverEvent.model_validate(event))


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--remover-type",
        type=str,
        choices=typing.get_args(IncrementalGraphRemoverType),
        help="Which remover to run (works, concepts, or work identifiers).",
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
    event = IncrementalRemoverEvent.from_argparser(args)

    handler(event, is_local=True)


if __name__ == "__main__":
    local_handler()
