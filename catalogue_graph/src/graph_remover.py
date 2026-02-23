import argparse
import typing
from datetime import datetime, timedelta

import polars as pl
import structlog

from clients.neptune_client import NeptuneClient
from models.events import BulkLoaderEvent, FullGraphRemoverEvent
from utils.argparse import add_cluster_connection_args, add_pipeline_event_args
from utils.aws import (
    df_from_s3_parquet,
    df_to_s3_parquet,
    get_csv_from_s3,
)
from utils.logger import ExecutionContext, get_trace_id, setup_logging
from utils.safety import validate_fractional_change
from utils.types import EntityType, FullGraphRemoverType, GraphRemoverFolder

logger = structlog.get_logger(__name__)

IDS_LOG_SCHEMA: dict = {"timestamp": pl.Date(), "id": pl.Utf8}


def get_previous_ids(event: FullGraphRemoverEvent) -> set[str]:
    """Return all IDs from the latest snapshot for the specified transformer and entity type."""
    s3_file_uri = event.get_s3_uri("parquet", "previous_ids_snapshot")
    df = df_from_s3_parquet(s3_file_uri)

    ids = pl.Series(df.select(pl.first())).to_list()
    logger.info(
        "Retrieved archived IDs from previous bulk loader file",
        count=len(ids),
    )
    return set(ids)


def get_current_ids(event: FullGraphRemoverEvent) -> set[str]:
    """Return all IDs from the latest bulk load file for the specified transformer and entity type."""
    bulk_loader_event = BulkLoaderEvent(**event.model_dump())
    s3_file_uri = bulk_loader_event.get_s3_uri()

    ids = set(row[":ID"] for row in get_csv_from_s3(s3_file_uri))
    logger.info(
        "Retrieved IDs from current bulk loader file",
        count=len(ids),
    )
    return ids


def update_node_ids_snapshot(event: FullGraphRemoverEvent, ids: set[str]) -> None:
    """Update the IDs snapshot with the latest IDs."""
    s3_file_uri = event.get_s3_uri("parquet", "previous_ids_snapshot")
    df = pl.DataFrame(list(ids))
    df_to_s3_parquet(df, s3_file_uri)


def log_ids(
    event: FullGraphRemoverEvent, ids: set[str], folder: GraphRemoverFolder
) -> None:
    """Append IDs which were added/removed as part of this run to the corresponding log file."""
    s3_file_uri = event.get_s3_uri("parquet", folder)

    try:
        df = df_from_s3_parquet(s3_file_uri)
    except (OSError, KeyError):
        logger.info(
            "File storing previously added/deleted IDs not found",
            note="This should only happen on the first run",
        )
        df = pl.DataFrame(schema=IDS_LOG_SCHEMA)

    ids_with_timestamp = {"timestamp": [datetime.now()] * len(ids), "id": list(ids)}
    new_data = pl.DataFrame(ids_with_timestamp, schema=IDS_LOG_SCHEMA)

    df = pl.concat([df, new_data], how="vertical")

    # Remove all IDs older than 1 year to prevent the file from getting too large
    one_year_ago = datetime.now() - timedelta(days=365)
    df = df.filter(pl.col("timestamp") >= one_year_ago)

    df_to_s3_parquet(df, s3_file_uri)


def handler(
    event: FullGraphRemoverEvent,
    execution_context: ExecutionContext | None = None,
) -> None:
    setup_logging(execution_context)

    try:
        # Retrieve a list of all ids which were loaded into the graph as part of the previous run
        previous_ids = get_previous_ids(event)
        is_first_run = False
    except (OSError, KeyError):
        logger.info(
            "File storing archived IDs not found",
            note="This should only happen on the first run",
        )
        previous_ids = set()
        is_first_run = True

    # Retrieve a list of ids which were loaded into the graph as part of the current run
    current_ids = get_current_ids(event)
    deleted_ids = set()
    added_ids = set()

    if not is_first_run:
        # IDs which were removed from/added to the transformer CSV output since the last time we ran the graph remover
        deleted_ids = previous_ids.difference(current_ids)
        added_ids = current_ids.difference(previous_ids)

        logger.info(
            "Bulk loader file changes since last run",
            deleted_ids=len(deleted_ids),
            added_ids=len(added_ids),
        )

    # This is part of a safety mechanism. If two sets of IDs differ by more than the DEFAULT_THRESHOLD
    # (set to 5%), an exception will be raised.
    validate_fractional_change(
        modified_size=len(deleted_ids),
        total_size=len(previous_ids),
        force_pass=event.force_pass,
    )

    if len(deleted_ids) > 0:
        # Delete the corresponding items from the graph
        client = NeptuneClient(event.environment)
        client.delete_entities_by_id(list(deleted_ids), event.entity_type)

    # Add ids which were deleted as part of this run to a log file storing all previously deleted ids
    log_ids(event, deleted_ids, "deleted_ids")
    log_ids(event, added_ids, "added_ids")
    logger.info("Successfully logged added and deleted IDs")

    # Update the list of all ids which have been loaded into the graph
    update_node_ids_snapshot(event, current_ids)
    logger.info("Successfully updated bulk loaded IDs snapshot")


def lambda_handler(event: dict, context: typing.Any) -> None:
    execution_context = ExecutionContext(
        trace_id=get_trace_id(context),
        pipeline_step="graph_remover",
    )
    handler(FullGraphRemoverEvent.model_validate(event), execution_context)


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    add_pipeline_event_args(parser, {"pipeline_date"})
    add_cluster_connection_args(parser, {"environment"})
    parser.add_argument(
        "--transformer-type",
        type=str,
        choices=typing.get_args(FullGraphRemoverType),
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
        "--force-pass",
        type=bool,
        help="Whether to override a safety check which prevents node/edge removal if the percentage of removed entities is above a certain threshold.",
        default=False,
    )

    args = parser.parse_args()
    event = FullGraphRemoverEvent(**args.__dict__)

    execution_context = ExecutionContext(
        trace_id=get_trace_id(),
        pipeline_step="graph_remover",
    )
    handler(
        event,
        execution_context,
    )


if __name__ == "__main__":
    local_handler()
