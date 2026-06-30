from collections.abc import Generator
from typing import Any

import structlog

from adapters.utils.adapter_store import AdapterStore
from core.source import BaseSource

logger = structlog.get_logger(__name__)


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
            table = self.adapter_store.get_active_namespace_records(self.snapshot_id)
            yield from self._with_enrichment(table.to_pylist())

    def _with_enrichment(self, rows: list[dict[str, Any]]) -> Generator[dict[str, Any]]:
        """Attach matching item-store content to each row by id.

        The join is a point lookup keyed by id (the instance id). When no items
        store is configured, rows pass through unchanged so non-FOLIO adapters are
        unaffected.
        """
        if self.items_store is None:
            yield from rows
            return

        items_by_id = self._items_by_id()
        for row in rows:
            row["enrichment_content"] = items_by_id.get(row["id"])
            yield row

    def _items_by_id(self) -> dict[str, Any]:
        # NOTE: for the prototype this loads the active item namespace and indexes it
        # in memory. At catalogue scale this should become a filtered lookup keyed by
        # the instance ids actually being transformed.
        #
        # The items store is read at its own current snapshot: `self.snapshot_id`
        # pins the *bib* store and is not a valid snapshot of the items table.
        assert self.items_store is not None  # guarded by _with_enrichment
        item_rows = self.items_store.get_active_namespace_records()
        return {row["id"]: row["content"] for row in item_rows.to_pylist()}
