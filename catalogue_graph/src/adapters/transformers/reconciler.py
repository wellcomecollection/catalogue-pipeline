"""Transformer step for Axiell and EBSCO.

Loads records for a changeset from Iceberg, applies a transform, and
indexes transformed documents into Elasticsearch.
"""

from datetime import UTC, datetime

import pyarrow as pa

from adapters.axiell.config import RECONCILER_LOCAL_CONFIG, RECONCILER_REST_API_CONFIG
from adapters.utils.iceberg import (
    IcebergTable,
    get_local_table,
    get_rest_api_table,
)
from adapters.utils.reconciler_store import ReconcilerStore


def build_reconciler_table(
    *,
    use_rest_api_table: bool = True,
    create_if_not_exists: bool = True,
) -> IcebergTable:
    if use_rest_api_table:
        return get_rest_api_table(RECONCILER_REST_API_CONFIG, create_if_not_exists)

    return get_local_table(RECONCILER_LOCAL_CONFIG, create_if_not_exists)


if __name__ == "__main__":
    reconciler_table = build_reconciler_table(use_rest_api_table=True)
    reconciler_store = ReconcilerStore(reconciler_table, "axiell")

    # Create a pyarrow table storing test mappings between IDs and GUIDs
    # TODO: This data will be read from the adapter store
    timestamp = pa.scalar(datetime.now(UTC), pa.timestamp("us", "UTC"))
    new_mappings = pa.table(
        {
            "namespace": ["axiell", "axiell", "axiell"],
            "id": ["cid1", "cid2", "cid3"],
            "guid": ["guid1", "guid2", "guid3"],
            "changeset": [None, None, None],
            "last_modified": [timestamp, timestamp, timestamp],
        }
    )
    new_mappings.cast(reconciler_store.schema)

    # Perform an incremental update on the reconciler store, adding new mappings
    # and updating existing mappings
    reconciler_store.incremental_update(new_mappings)

    # TODO: Transform updated data and send them to Elasticsearch
