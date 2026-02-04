"""Generic record writer callback for OAI-PMH harvesting.

Persists harvested OAI-PMH records to an Iceberg adapter store and returns
metadata about the changes for downstream processing.
"""

from __future__ import annotations

import json
from typing import Any

import pyarrow as pa
from lxml import etree
from oai_pmh_client.models import Record

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ARROW_SCHEMA
from adapters.utils.window_harvester import WindowCallbackResult


def _serialize_metadata(record: Record) -> str | None:
    """Serialize OAI-PMH metadata element to XML string."""
    metadata = getattr(record, "metadata", None)
    if metadata is None:
        return None
    return etree.tostring(metadata, encoding="unicode", pretty_print=False)


class WindowRecordWriter:
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
        window_range: str,
    ) -> None:
        """Initialize the record writer.

        Args:
            namespace: Namespace for records in the adapter store.
            table_client: AdapterStore instance for persisting records.
            job_id: Job identifier for tagging records.
            window_range: Human-readable window range for tagging.
        """
        self.namespace = namespace
        self.table_client = table_client
        self.job_id = job_id
        self.window_range = window_range

    def __call__(
        self,
        records: list[tuple[str, Record]],
    ) -> WindowCallbackResult:
        """Persist records and return change metadata.

        Args:
            records: List of (identifier, Record) tuples from OAI-PMH.

        Returns:
            Dictionary with tags including changeset_id and record_ids_changed.
        """
        rows: list[dict[str, Any]] = []

        for identifier, record in records:
            content = _serialize_metadata(record)
            rows.append(
                {
                    "namespace": self.namespace,
                    "id": identifier,
                    "content": content,
                    "last_modified": record.header.datestamp,
                    "deleted": content is None,
                }
            )

        tags: dict[str, str] = {
            "job_id": self.job_id,
            "window_range": self.window_range,
        }
        changeset_id: str | None = None
        updated_record_ids: list[str] | None = None

        if rows:
            table = pa.Table.from_pylist(rows, schema=ARROW_SCHEMA)
            update = self.table_client.incremental_update(table)
            if update:
                changeset_id = update.changeset_id
                updated_record_ids = update.updated_record_ids

        if changeset_id:
            tags["changeset_id"] = changeset_id

        if updated_record_ids:
            tags["record_ids_changed"] = json.dumps(updated_record_ids)

        return {"tags": tags}
