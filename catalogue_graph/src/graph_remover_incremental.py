import argparse
import typing

import polars as pl

from models.events import IncrementalGraphRemoverEvent
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
from utils.types import CatalogueTransformerType, EntityType


def get_remover(
    event: IncrementalGraphRemoverEvent, is_local: bool
) -> BaseGraphRemover:
    es_mode: ElasticsearchMode = "public" if is_local else "private"

    if event.transformer_type == "catalogue_works":
        return CatalogueWorksGraphRemover(event, es_mode)
    if event.transformer_type == "catalogue_concepts":
        return CatalogueConceptsGraphRemover(event, es_mode)
    if event.transformer_type == "catalogue_work_identifiers":
        return CatalogueWorkIdentifiersGraphRemover(event, es_mode)

    raise ValueError(f"Unknown remover type: '{event.transformer_type}'")


def handler(event: IncrementalGraphRemoverEvent, is_local: bool = False) -> None:
    remover = get_remover(event, is_local)
    deleted_ids = remover.remove()

    # Write removed IDs to a parquet file
    s3_file_uri = event.get_remover_s3_uri("deleted_ids")
    df_to_s3_parquet(pl.DataFrame(deleted_ids), s3_file_uri)

    print(f"List of deleted IDs saved to '{s3_file_uri}'.")


def lambda_handler(event: dict, context: typing.Any) -> None:
    handler(IncrementalGraphRemoverEvent.model_validate(event))


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

    handler(event, is_local=True)


if __name__ == "__main__":
    local_handler()
