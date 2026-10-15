"""Shared helpers for FOLIO transformer tests."""

from typing import Any
from uuid import uuid1

from adapters.extractors.oai_pmh.folio.enrichment.models import FolioInventoryRecord
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg import LocalIcebergTableConfig, get_local_table
from tests.adapters.conftest import adapter_records_to_table

INVENTORY_NAMESPACE = "folio-inventory"


def folio_record(instance_id: str, items: list[dict[str, Any]]) -> dict[str, Any]:
    """A minimal raw items-and-holdings record, as the endpoint returns it."""
    return {"instanceId": instance_id, "holdings": [], "items": items}


def make_inventory_store(records: dict[str, dict[str, Any]]) -> AdapterStore:
    """Build a throwaway local store holding the given raw records."""
    config = LocalIcebergTableConfig(
        table_name=str(uuid1()),
        namespace="test",
        db_name="test_catalog",
    )
    table = get_local_table(config)
    store = AdapterStore(table, INVENTORY_NAMESPACE)
    if records:
        rows = adapter_records_to_table(
            [
                {
                    "id": instance_id,
                    "content": FolioInventoryRecord(data=record).to_store_content(),
                }
                for instance_id, record in records.items()
            ],
            namespace=INVENTORY_NAMESPACE,
        )
        store.incremental_update(rows)
    return store
