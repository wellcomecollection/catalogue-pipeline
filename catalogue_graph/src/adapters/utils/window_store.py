from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from threading import Lock
from typing import Any

import pyarrow as pa
from pyiceberg.expressions import EqualTo
from pyiceberg.partitioning import PartitionField, PartitionSpec
from pyiceberg.schema import Schema
from pyiceberg.table import Table as IcebergTable
from pyiceberg.transforms import HourTransform
from pyiceberg.types import (
    IntegerType,
    ListType,
    MapType,
    NestedField,
    StringType,
    TimestamptzType,
)

__all__ = ["WindowStatusRecord", "IcebergWindowStore"]


@dataclass(slots=True)
class WindowStatusRecord:
    """Represents the persistence payload for a harvesting window."""

    window_key: str
    window_start: datetime
    window_end: datetime
    state: str
    attempts: int
    last_error: str | None
    record_ids: tuple[str, ...]
    updated_at: datetime
    tags: dict[str, str] | None = None


WINDOW_STATUS_SCHEMA = Schema(
    NestedField(1, "window_key", StringType(), required=True),
    NestedField(2, "window_start", TimestamptzType(), required=True),
    NestedField(3, "window_end", TimestamptzType(), required=True),
    NestedField(4, "state", StringType(), required=True),
    NestedField(5, "attempts", IntegerType(), required=True),
    NestedField(6, "last_error", StringType(), required=False),
    NestedField(7, "updated_at", TimestamptzType(), required=True),
    NestedField(
        8,
        "record_ids",
        ListType(10, StringType(), element_required=False),
        required=False,
    ),
    NestedField(
        9,
        "tags",
        MapType(11, StringType(), 12, StringType(), value_required=False),
        required=False,
    ),
)

WINDOW_STATUS_PARTITION_SPEC = PartitionSpec(
    PartitionField(2, 1000, HourTransform(), "window_start_hour")
)

WINDOW_STATUS_ARROW_FIELDS: list[pa.Field] = [
    pa.field("window_key", pa.string(), nullable=False),
    pa.field("window_start", pa.timestamp("us", tz="UTC"), nullable=False),
    pa.field("window_end", pa.timestamp("us", tz="UTC"), nullable=False),
    pa.field("state", pa.string(), nullable=False),
    pa.field("attempts", pa.int32(), nullable=False),
    pa.field("last_error", pa.string(), nullable=True),
    pa.field("updated_at", pa.timestamp("us", tz="UTC"), nullable=False),
    pa.field("record_ids", pa.list_(pa.string()), nullable=True),
    pa.field("tags", pa.map_(pa.string(), pa.string()), nullable=True),
]

WINDOW_STATUS_ARROW_SCHEMA = pa.schema(WINDOW_STATUS_ARROW_FIELDS)


def _column(value: Any) -> list[Any]:
    """Wrap a value in a single-row list for PyArrow's from_pydict input."""

    return [value]


class IcebergWindowStore:
    """Persists harvesting window status rows in an Apache Iceberg table."""

    def __init__(self, table: IcebergTable) -> None:
        self._table = table
        self._lock = Lock()

    @property
    def table(self) -> IcebergTable:
        return self._table

    def load_status_map(self) -> dict[str, dict[str, Any]]:
        """Load all window summaries keyed by their window identifier."""
        scan = self._table.scan()
        arrow_table = scan.to_arrow()
        if arrow_table is None or arrow_table.num_rows == 0:
            return {}
        rows = [self._normalize_row(row) for row in arrow_table.to_pylist()]
        return {row["window_key"]: row for row in rows}

    def upsert(self, record: WindowStatusRecord) -> None:
        """Delete any existing row for the window then append the new status."""
        payload: dict[str, list[Any]] = {
            "window_key": _column(record.window_key),
            "window_start": _column(record.window_start),
            "window_end": _column(record.window_end),
            "state": _column(record.state),
            "attempts": _column(record.attempts),
            "last_error": _column(record.last_error),
            "updated_at": _column(record.updated_at),
            "record_ids": _column(list(record.record_ids)),
            "tags": _column(record.tags),
        }
        table = self._table
        with self._lock:
            table.delete(EqualTo("window_key", record.window_key))
            table.append(
                pa.Table.from_pydict(payload, schema=WINDOW_STATUS_ARROW_SCHEMA)
            )

    def list_by_state(self, state: str) -> list[dict[str, Any]]:
        """Return rows filtered by state (e.g. success, failed)."""
        scan = self._table.scan().filter(EqualTo("state", state))
        arrow_table = scan.to_arrow()
        if arrow_table is None or arrow_table.num_rows == 0:
            return []
        return [self._normalize_row(row) for row in arrow_table.to_pylist()]

    @staticmethod
    def _normalize_row(row: dict[str, Any]) -> dict[str, Any]:
        tags = row.get("tags")
        if tags is not None and not isinstance(tags, dict):
            # Arrow map columns materialize as list[tuple]; coerce to dict for callers.
            row = dict(row)
            row["tags"] = dict(tags)
        return row
