import json
from typing import Any

import pyarrow as pa
from lxml import etree
from oai_pmh_client.models import Record

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.schemata import ARROW_SCHEMA
from adapters.utils.window_harvester import WindowCallbackResult

WINDOW_RECORD_WRITER_SCHEMA = ARROW_SCHEMA.append(
    pa.field("last_modified", pa.timestamp("us", "UTC"))
)


def _serialize_metadata(record: Record) -> str | None:
    metadata = getattr(record, "metadata", None)
    if metadata is None:
        return None
    return etree.tostring(metadata, encoding="unicode", pretty_print=False)


class WindowRecordWriter:
    def __init__(
        self,
        *,
        namespace: str,
        table_client: AdapterStore,
        job_id: str,
        window_range: str,
    ) -> None:
        self.namespace = namespace
        self.table_client = table_client
        self.job_id = job_id
        self.window_range = window_range

    def __call__(
        self,
        records: list[tuple[str, Record]],
    ) -> WindowCallbackResult:
        rows: list[dict[str, Any]] = []
        record_ids: list[str] = []

        for identifier, record in records:
            rows.append(
                {
                    "namespace": self.namespace,
                    "id": identifier,
                    "content": _serialize_metadata(record),
                    "last_modified": record.header.datestamp,
                }
            )
            record_ids.append(identifier)

        tags: dict[str, str] = {
            "job_id": self.job_id,
            "window_range": self.window_range,
        }
        changeset_id: str | None = None
        updated_record_ids: list[str] | None = None

        if rows:
            table = pa.Table.from_pylist(rows, schema=WINDOW_RECORD_WRITER_SCHEMA)
            update = self.table_client.incremental_update(table)
            if update:
                changeset_id = update.changeset_id
                updated_record_ids = update.updated_record_ids

        if changeset_id:
            tags["changeset_id"] = changeset_id

        if updated_record_ids:
            tags["record_ids_changed"] = json.dumps(updated_record_ids)

        return {"record_ids": record_ids, "tags": tags}
