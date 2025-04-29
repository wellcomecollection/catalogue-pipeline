import argparse
import typing
from datetime import datetime, timedelta

import polars as pl

import config
from transformers.create_transformer import EntityType, TransformerType
from utils.aws import (
    df_from_s3_parquet,
    df_to_s3_parquet,
    get_csv_from_s3,
    get_neptune_client,
)
from utils.safety import validate_fractional_change

IDS_LOG_SCHEMA: dict = {"timestamp": pl.Date(), "id": pl.Utf8}

IDS_SNAPSHOT_FOLDER = "graph_remover/previous_ids_snapshot"
DELETED_IDS_FOLDER = "graph_remover/deleted_ids"
ADDED_IDS_FOLDER = "graph_remover/added_ids"


def get_previous_ids(
    transformer_type: TransformerType, entity_type: EntityType
) -> set[str]:
    """Return all IDs from the latest snapshot for the specified transformer and entity type."""
    s3_file_uri = f"s3://{config.INGESTOR_S3_BUCKET}/{IDS_SNAPSHOT_FOLDER}/{transformer_type}__{entity_type}.parquet"
    df = df_from_s3_parquet(s3_file_uri)

    ids = pl.Series(df.select(pl.first())).to_list()
    print(f"Retrieved {len(ids)} ids archived from a previous bulk loader file.")
    return set(ids)


def get_current_ids(
    transformer_type: TransformerType, entity_type: EntityType
) -> set[str]:
    """Return all IDs from the latest bulk load file for the specified transformer and entity type."""
    s3_file_uri = (
        f"s3://{config.S3_BULK_LOAD_BUCKET_NAME}/{transformer_type}__{entity_type}.csv"
    )

    print("OPENING URI", s3_file_uri)

    ids = set(row[":ID"] for row in get_csv_from_s3(s3_file_uri))
    print(f"Retrieved {len(ids)} ids from the current bulk loader file.")
    return ids


def update_node_ids_snapshot(
    transformer_type: TransformerType, entity_type: EntityType, ids: set[str]
) -> None:
    """Update the IDs snapshot with the latest IDs."""
    s3_file_uri = f"s3://{config.INGESTOR_S3_BUCKET}/{IDS_SNAPSHOT_FOLDER}/{transformer_type}__{entity_type}.parquet"
    df = pl.DataFrame(list(ids))
    df_to_s3_parquet(df, s3_file_uri)


def log_ids(
    ids: set[str],
    transformer_type: TransformerType,
    entity_type: EntityType,
    folder: str,
) -> None:
    """Append IDs which were added/removed as part of this run to the corresponding log file."""
    s3_file_uri = f"s3://{config.INGESTOR_S3_BUCKET}/{folder}/{transformer_type}__{entity_type}.parquet"

    try:
        df = df_from_s3_parquet(s3_file_uri)
    except (OSError, KeyError):
        print(
            "File storing previously added/deleted ids not found. This should only happen on the first run."
        )
        df = pl.DataFrame(schema=IDS_LOG_SCHEMA)

    ids_with_timestamp = {"timestamp": [datetime.now()] * len(ids), "id": list(ids)}
    new_data = pl.DataFrame(ids_with_timestamp, schema=IDS_LOG_SCHEMA)

    df = pl.concat([df, new_data], how="vertical")

    # Remove all IDs older than 1 year to prevent the file from getting too large
    one_year_ago = datetime.now() - timedelta(days=365)
    df = df.filter(pl.col("timestamp") >= one_year_ago)

    df_to_s3_parquet(df, s3_file_uri)


def delete_ids_from_neptune(
    deleted_ids: set[str], entity_type: EntityType, is_local: bool
) -> None:
    """Delete all nodes/edges with matching IDs from the Neptune cluster"""
    client = get_neptune_client(is_local)
    if entity_type == "nodes":
        client.delete_nodes_by_id(list(deleted_ids))
    else:
        client.delete_edges_by_id(list(deleted_ids))


def handler(
    transformer_type: TransformerType,
    entity_type: EntityType,
    disable_safety_check: bool,
    is_local: bool = False,
) -> None:
    try:
        # Retrieve a list of all ids which were loaded into the graph as part of the previous run
        previous_ids = get_previous_ids(transformer_type, entity_type)
        is_first_run = False
    except (OSError, KeyError):
        print(
            "File storing archived ids not found. This should only happen on the first run."
        )
        previous_ids = set()
        is_first_run = True

    # Retrieve a list of ids which were loaded into the graph as part of the current run
    current_ids = get_current_ids(transformer_type, entity_type)
    deleted_ids = set()
    added_ids = set()

    if not is_first_run:
        # IDs which were removed from/added to the transformer CSV output since the last time we ran the graph remover
        deleted_ids = previous_ids.difference(current_ids)
        added_ids = current_ids.difference(previous_ids)

        print(
            "Bulk loader file changes since the last run:\n",
            f"   Deleted ids: {len(deleted_ids)}\n",
            f"   Added ids: {len(added_ids)}",
        )

    # This is part of a safety mechanism. If two sets of IDs differ by more than the DEFAULT_THRESHOLD
    # (set to 5%), an exception will be raised.
    validate_fractional_change(
        modified_size=len(deleted_ids),
        total_size=len(previous_ids),
        force_pass=disable_safety_check,
    )

    if len(deleted_ids) > 0:
        # Delete the corresponding items from the graph
        delete_ids_from_neptune(deleted_ids, entity_type, is_local)

    # Add ids which were deleted as part of this run to a log file storing all previously deleted ids
    log_ids(deleted_ids, transformer_type, entity_type, DELETED_IDS_FOLDER)
    log_ids(added_ids, transformer_type, entity_type, ADDED_IDS_FOLDER)
    print("Successfully logged added and deleted ids.")

    # Update the list of all ids which have been loaded into the graph
    update_node_ids_snapshot(transformer_type, entity_type, current_ids)
    print("Successfully updated bulk loaded ids snapshot.")


def lambda_handler(event: dict, context: typing.Any) -> None:
    transformer_type = event["transformer_type"]
    entity_type = event["entity_type"]
    override_safety_check = event.get("override_safety_check", False)

    handler(transformer_type, entity_type, override_safety_check)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--transformer-type",
        type=str,
        choices=typing.get_args(TransformerType),
        help="Which transformer's output to bulk load.",
        required=True,
    )
    parser.add_argument(
        "--entity-type",
        type=str,
        choices=typing.get_args(EntityType),
        help="Which entity type to transform using the specified transformer (nodes or edges).",
        required=True,
    )
    parser.add_argument(
        "--disable-safety-check",
        type=bool,
        help="Whether to override a safety check which prevents node/edge removal if the percentage of removed entities is above a certain threshold.",
        default=False,
    )

    args = parser.parse_args()

    handler(**args.__dict__, is_local=True)


if __name__ == "__main__":
    local_handler()
