import argparse
import typing
from pathlib import PurePosixPath

import polars as pl

import config
from models.events import IncrementalRemoverEvent
from removers.catalogue_concepts_remover import CatalogueConceptsGraphRemover
from removers.catalogue_works_remover import CatalogueWorksGraphRemover
from utils.aws import (
    df_to_s3_parquet,
)
from utils.types import EntityType, IncrementalGraphRemoverType


def get_s3_uri(event: IncrementalRemoverEvent) -> str:
    parts: list[str] = [config.INCREMENTAL_GRAPH_REMOVER_S3_PREFIX, event.pipeline_date]

    if event.window is not None:
        parts += ["windows", event.window.to_formatted_string()]

    prefix = PurePosixPath(*parts)

    return (
        f"s3://{config.CATALOGUE_GRAPH_S3_BUCKET}/{prefix}/{event.remover_type}.parquet"
    )


def get_remover(event: IncrementalRemoverEvent, is_local: bool):
    es_mode = "public" if is_local else "private"

    if event.remover_type == "works":
        return CatalogueWorksGraphRemover(event, es_mode)
    if event.remover_type == "concepts":
        return CatalogueConceptsGraphRemover(event, es_mode)

    raise ValueError(f"Unknown remover type: '{event.remover_type}'")


def handler(event: IncrementalRemoverEvent, is_local: bool = False) -> None:
    remover = get_remover(event, is_local)
    deleted_ids = remover.remove()

    # Write removed IDs to a parquet file
    s3_file_uri = get_s3_uri(event)
    df = pl.DataFrame(deleted_ids)
    df_to_s3_parquet(df, s3_file_uri)

    # Work identifiers?


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--remover-type",
        type=str,
        choices=typing.get_args(IncrementalGraphRemoverType),
        help="Which remover to run (works or concepts).",
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

    args = parser.parse_args()
    event = IncrementalRemoverEvent(**args.__dict__)

    handler(event, is_local=True)


if __name__ == "__main__":
    local_handler()
