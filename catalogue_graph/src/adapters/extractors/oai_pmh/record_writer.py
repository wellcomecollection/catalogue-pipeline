"""Generic record writer callback for OAI-PMH harvesting.

Persists harvested OAI-PMH records to an Iceberg adapter store and returns
metadata about the changes for downstream processing.
"""

from __future__ import annotations

from typing import Any

import pyarrow as pa
import structlog
from lxml import etree
from oai_pmh_client.models import Record

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ADAPTER_STORE_ARROW_SCHEMA
from adapters.utils.window_harvester import WindowCallbackResult

logger = structlog.get_logger(__name__)


def serialize_metadata(record: Record) -> str | None:
    """Serialize OAI-PMH metadata element to XML string."""
    metadata = getattr(record, "metadata", None)
    if metadata is None:
        return None
    return etree.tostring(metadata, encoding="unicode", pretty_print=False)


def build_adapter_store_row(
    *, namespace: str, identifier: str, record: Record
) -> dict[str, Any]:
    """Serialize one OAI-PMH record into an adapter store row.

    The single definition of the adapter store row shape. A record the source
    serves without metadata is stored as a tombstone (``deleted=True``).
    """
    content = serialize_metadata(record)
    return {
        "namespace": namespace,
        "id": identifier,
        "content": content,
        "last_modified": record.header.datestamp,
        "deleted": content is None,
    }


class RecordWriter:
    """Callback for persisting harvested records to an adapter store.

    This callback is invoked by WindowHarvestManager for each batch of records
    and handles serialization, storage, and change tracking.
    """

    def __init__(
        self,
        *,
        namespace: str,
        table_client: AdapterStore,
        job_id: str,
        extra_tags: dict[str, str] | None = None,
    ) -> None:
        """Initialize the record writer.

        Args:
            namespace: Namespace for records in the adapter store.
            table_client: AdapterStore instance for persisting records.
            job_id: Job identifier for tagging records.
            extra_tags: Additional tags merged onto every write, alongside
                job_id. For example, window mode tags rows with the harvested
                window range; the loader's id mode passes none, so rows are
                tagged with the job id alone.
        """
        self.namespace = namespace
        self.table_client = table_client
        self.job_id = job_id
        self.extra_tags = extra_tags

    def _build_rows(self, records: list[tuple[str, Record]]) -> list[dict[str, Any]]:
        """Serialize (identifier, Record) pairs into adapter store rows."""
        return [
            build_adapter_store_row(
                namespace=self.namespace, identifier=identifier, record=record
            )
            for identifier, record in records
        ]

    @property
    def _tags(self) -> dict[str, str]:
        tags = {"job_id": self.job_id}
        if self.extra_tags:
            tags.update(self.extra_tags)
        return tags

    def __call__(
        self,
        records: list[tuple[str, Record]],
    ) -> WindowCallbackResult:
        """Persist records and return change metadata.

        Args:
            records: List of (identifier, Record) tuples from OAI-PMH.

        Returns:
            WindowCallbackResult with tags, changeset_id, and upserted record IDs.
        """
        rows = self._build_rows(records)

        if rows:
            table = pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)
            update = self.table_client.incremental_update(table)
            if update:
                return WindowCallbackResult(
                    tags=self._tags,
                    changeset_id=update.changeset_id,
                    upserted_record_ids=update.upserted_record_ids,
                )

        return WindowCallbackResult(
            tags=self._tags, changeset_id=None, upserted_record_ids=[]
        )


class BufferedRecordWriter(RecordWriter):
    """Record writer that buffers rows across calls and commits per flush.

    In the default (unbuffered) mode every window costs one Iceberg commit for
    its records, which dominates wall-clock time on slow catalogs (e.g. S3
    Tables) when backfilling hundreds of windows. This writer instead
    accumulates rows across windows and commits a single
    ``AdapterStore.incremental_update`` per ``flush()`` call.

    Semantics compared to ``RecordWriter``:

    - ``__call__`` only buffers: it returns ``changeset_id=None`` and an empty
      ``upserted_record_ids`` list, so per-window summaries carry no changeset
      ids or upsert counts. Changeset ids and upserted-record counts in
      buffered mode are per-flush, not per-window; they accumulate on
      :attr:`changeset_ids` and :attr:`upserted_record_count` and must be added
      to the loader response separately.
    - If the same record id is buffered more than once between flushes, the
      most recently buffered row wins (matching last-write-wins behaviour of
      sequential per-window commits).
    - Buffered rows are only durable after ``flush()``: a crash loses all rows
      buffered since the previous flush.
    """

    def __init__(
        self,
        *,
        namespace: str,
        table_client: AdapterStore,
        job_id: str,
        extra_tags: dict[str, str] | None = None,
        flush_threshold: int | None = None,
    ) -> None:
        """Initialize the buffered writer.

        Args:
            flush_threshold: Commit automatically once this many rows are
                buffered. ``None`` (the default, and what the loader's window
                mode uses) means the caller drives every flush explicitly.
        """
        super().__init__(
            namespace=namespace,
            table_client=table_client,
            job_id=job_id,
            extra_tags=extra_tags,
        )
        # Keyed by record id so later windows overwrite earlier buffered rows.
        self._buffer: dict[str, dict[str, Any]] = {}
        self._flush_threshold = flush_threshold
        self.changeset_ids: list[str] = []
        self.upserted_record_count: int = 0

    @property
    def pending(self) -> int:
        """Rows buffered since the last flush."""
        return len(self._buffer)

    def __call__(
        self,
        records: list[tuple[str, Record]],
    ) -> WindowCallbackResult:
        """Buffer records for a later flush and return placeholder metadata."""
        for row in self._build_rows(records):
            self._buffer[row["id"]] = row

        if self._flush_threshold and len(self._buffer) >= self._flush_threshold:
            self.flush()

        return WindowCallbackResult(
            tags=self._tags, changeset_id=None, upserted_record_ids=[]
        )

    def flush(self) -> str | None:
        """Commit all buffered rows in a single incremental update.

        Returns:
            The changeset id of the commit, or None if the buffer was empty or
            the update was a no-op (no rows changed).
        """
        if not self._buffer:
            return None

        rows = list(self._buffer.values())
        self._buffer = {}

        table = pa.Table.from_pylist(rows, schema=ADAPTER_STORE_ARROW_SCHEMA)
        update = self.table_client.incremental_update(table)

        logger.info(
            "Flushed buffered records",
            row_count=len(rows),
            changeset_id=update.changeset_id if update else None,
        )

        if update:
            self.changeset_ids.append(update.changeset_id)
            self.upserted_record_count += len(update.upserted_record_ids)
            return update.changeset_id
        return None
