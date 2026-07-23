from collections.abc import Generator
from itertools import batched
from typing import Any

from pyiceberg.expressions import In

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.adapter_store_source import AdapterStoreSource

# Bib rows are enriched in batches of this size, so the per-batch item lookup (and the
# items it holds in memory) stays bounded regardless of how many rows are transformed.
ITEM_ENRICHMENT_BATCH_SIZE = 10_000


class FolioStoreSource(AdapterStoreSource):
    """AdapterStoreSource that joins a secondary items store onto each bib row.

    FOLIO-specific: the OAI-PMH bib record cannot carry the stable item/holdings
    UUIDs, so the enrichment step stores them separately and this source attaches
    them (as `enrichment_content`) via a point lookup keyed by id (the instance id).
    """

    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None = None,
        items_store: AdapterStore | None = None,
    ):
        super().__init__(adapter_store, changeset_ids, snapshot_id)
        self.items_store = items_store

    def _process_rows(self, rows: list[dict[str, Any]]) -> Generator[dict[str, Any]]:
        """Attach matching item-store content to each row by id.

        When no items store is configured, rows pass through unchanged.
        """
        if self.items_store is None:
            yield from rows
            return

        # Enrich in bounded batches: fetch only the items for the ids in each chunk, so
        # the in-memory items dict never exceeds one batch (the join was previously a
        # full-namespace load).
        for chunk in batched(rows, ITEM_ENRICHMENT_BATCH_SIZE):
            items_by_id = self._items_by_id([row["id"] for row in chunk])
            for row in chunk:
                row["enrichment_content"] = items_by_id.get(row["id"])
                yield row

    def _items_by_id(self, ids: list[str]) -> dict[str, Any]:
        # Fetch only the items for the ids being transformed (a filtered scan on the
        # sorted id column), not the whole namespace.
        #
        # The items store is read at its own current snapshot: `self.snapshot_id`
        # pins the *bib* store and is not a valid snapshot of the items table.
        assert self.items_store is not None  # guarded by _process_rows
        if not ids:
            return {}
        item_rows = self.items_store.get_active_namespace_records(
            iceberg_filter=In("id", ids)
        )
        return {row["id"]: row["content"] for row in item_rows.to_pylist()}
