from collections.abc import Generator
from itertools import batched
from typing import Any

import structlog
from pyiceberg.expressions import In

from adapters.utils.adapter_store import AdapterStore
from core.source import BaseSource

logger = structlog.get_logger(__name__)

# Bib rows are enriched in batches of this size, so the per-batch item lookup (and the
# items it holds in memory) stays bounded regardless of how many rows are transformed.
ITEM_ENRICHMENT_BATCH_SIZE = 10_000


class AdapterStoreSource(BaseSource):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None = None,
        items_store: AdapterStore | None = None,
    ):
        self.adapter_store = adapter_store
        self.changeset_ids = changeset_ids
        self.snapshot_id = snapshot_id
        # Optional secondary store joined onto each record by id. Used by the FOLIO
        # adapter to attach enriched item/holdings content (with UUIDs) that the
        # OAI-PMH bib record cannot carry.
        self.items_store = items_store

    def stream_raw(self) -> Generator[dict[str, Any]]:
        if self.changeset_ids:
            for changeset_id in self.changeset_ids:
                table = self.adapter_store.get_records_by_changeset(
                    changeset_id, self.snapshot_id
                )
                yield from self._with_enrichment(table.to_pylist())
        else:
            logger.info("No changeset_id provided; performing full reindex of records.")

            # During a full reindex we are writing into an empty index,
            # so no need to include deleted rows to overwrite documents.
            # Stream record batches so the full table need not be materialised
            # at once. Close the reader on exit so an abandoned stream (e.g. a
            # consumer error mid-reindex) does not leave prefetch reads running.
            batches = self.adapter_store.stream_active_namespace_records(
                self.snapshot_id
            )
            try:
                for batch in batches:
                    yield from self._with_enrichment(batch.to_pylist())
            finally:
                batches.close()

    def _with_enrichment(self, rows: list[dict[str, Any]]) -> Generator[dict[str, Any]]:
        """Attach matching item-store content to each row by id.

        The join is a point lookup keyed by id (the instance id). When no items
        store is configured, rows pass through unchanged so non-FOLIO adapters are
        unaffected.
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
        assert self.items_store is not None  # guarded by _with_enrichment
        if not ids:
            return {}
        item_rows = self.items_store.get_active_namespace_records(
            iceberg_filter=In("id", ids)
        )
        return {row["id"]: row["content"] for row in item_rows.to_pylist()}
