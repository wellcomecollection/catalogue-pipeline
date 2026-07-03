"""Shared helpers for FOLIO transformer tests."""

from uuid import uuid1

from adapters.extractors.oai_pmh.folio.enrichment.models import FolioEnrichedInstance
from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg import LocalIcebergTableConfig, get_local_table
from tests.adapters.conftest import adapter_records_to_table

ITEMS_NAMESPACE = "folio-items"


def make_items_store(records: dict[str, FolioEnrichedInstance]) -> AdapterStore:
    """Build a throwaway local items store holding the given enriched instances."""
    config = LocalIcebergTableConfig(
        table_name=str(uuid1()),
        namespace="test",
        db_name="test_catalog",
    )
    table = get_local_table(config)
    store = AdapterStore(table, ITEMS_NAMESPACE)
    if records:
        rows = adapter_records_to_table(
            [
                {"id": instance_id, "content": instance.to_store_content()}
                for instance_id, instance in records.items()
            ],
            namespace=ITEMS_NAMESPACE,
        )
        store.incremental_update(rows)
    return store
