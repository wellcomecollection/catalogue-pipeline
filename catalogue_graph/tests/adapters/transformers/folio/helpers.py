"""Shared helpers for FOLIO transformer tests."""

from typing import Any
from uuid import uuid1

from adapters.extractors.oai_pmh.folio.enrichment.models import FolioInventoryRecord
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg import LocalIcebergTableConfig, get_local_table
from tests.adapters.conftest import adapter_records_to_table
from tests.test_utils import load_json_fixture

INVENTORY_NAMESPACE = "folio-inventory"

# A response captured from the tenant for wellcomecollection/platform#6581: a serial with 27
# holdings records and 61 items, barcodes replaced with placeholders.
INVENTORY_FIXTURE = "folio/items_and_holdings_64125966.json"


def fixture_record(instance_id: str | None = None) -> dict[str, Any]:
    """The fixture record, optionally under another instance id."""
    record: dict[str, Any] = load_json_fixture(INVENTORY_FIXTURE)
    if instance_id is not None:
        record["instanceId"] = instance_id
    return record


def fixture_item_ids() -> list[str]:
    return [item["id"] for item in fixture_record()["items"]]


def folio_record(instance_id: str, items: list[dict[str, Any]]) -> dict[str, Any]:
    """A minimal raw record, for edge cases the fixture doesn't cover.

    Items are linked to a single holdings record, as the endpoint returns them.
    """
    if not items:
        return {"instanceId": instance_id, "holdings": [], "items": []}
    holdings_id = f"holdings-{instance_id}"
    return {
        "instanceId": instance_id,
        "holdings": [{"id": holdings_id}],
        "items": [{"holdingsRecordId": holdings_id, **item} for item in items],
    }


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
