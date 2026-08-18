from collections.abc import Generator
from typing import Any

import structlog
from pyiceberg.expressions import In

from adapters.utils.adapter_store import AdapterStore
from core.source import BaseSource

logger = structlog.get_logger(__name__)


class RecordSource(BaseSource):
    """The contract adapter transformers require of their record source."""

    snapshot_id: int | None

    unmatched_ids: list[str]
    """Requested ids (id-mode only) that matched no row in the store. Only
    meaningful once `stream_raw` has been fully consumed; empty otherwise."""


class AdapterStoreSource(RecordSource):
    def __init__(
        self,
        adapter_store: AdapterStore,
        changeset_ids: list[str],
        snapshot_id: int | None = None,
        ids: list[str] | None = None,
    ):
        if changeset_ids and ids:
            raise ValueError(
                "changeset_ids and ids are mutually exclusive; supply one or "
                "the other, not both"
            )
        self.adapter_store = adapter_store
        self.changeset_ids = changeset_ids
        self.snapshot_id = snapshot_id
        self.ids = ids
        self.unmatched_ids = []

    def stream_raw(self) -> Generator[dict[str, Any]]:
        if self.changeset_ids:
            # Includes soft-deleted rows, needed to overwrite documents downstream.
            # Convert one batch at a time so the Python dicts never hold the
            # whole (possibly multi-changeset) table alongside the Arrow copy.
            table = self.adapter_store.get_records_by_changesets(
                self.changeset_ids, self.snapshot_id
            )
            for batch in table.to_batches():
                yield from self._process_rows(batch.to_pylist())
        elif self.ids:
            # Includes soft-deleted rows, for the same reason as the changeset
            # path: tombstones must overwrite live documents downstream. The
            # store is sorted on id, so this filter prunes row groups rather
            # than scanning the whole namespace.
            # Deduped so a requested id given twice isn't double-counted in
            # unmatched_ids below.
            unique_ids = list(dict.fromkeys(self.ids))
            table = self.adapter_store.get_namespace_records(
                In("id", unique_ids), self.snapshot_id
            )
            seen_ids: set[str] = set()
            for batch in table.to_batches():
                rows = batch.to_pylist()
                seen_ids.update(row["id"] for row in rows)
                yield from self._process_rows(rows)

            self.unmatched_ids = [id_ for id_ in unique_ids if id_ not in seen_ids]
            if self.unmatched_ids:
                logger.warning(
                    "Requested ids matched no row in the store",
                    unmatched_ids=self.unmatched_ids,
                    unmatched_count=len(self.unmatched_ids),
                )
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
                    yield from self._process_rows(batch.to_pylist())
            finally:
                batches.close()

    def _process_rows(self, rows: list[dict[str, Any]]) -> Generator[dict[str, Any]]:
        """Hook for source-specific per-batch processing (e.g. the FOLIO item join
        in `FolioStoreSource`). The base source passes rows through unchanged."""
        yield from rows
