from __future__ import annotations

from datetime import datetime
from threading import Lock
from typing import Any

import pyarrow as pa
from pyiceberg.expressions import (
    And,
    BooleanExpression,
    EqualTo,
    GreaterThanOrEqual,
    In,
    LessThan,
)
from pyiceberg.io.pyarrow import schema_to_pyarrow
from pyiceberg.schema import Schema
from pyiceberg.table import ALWAYS_TRUE
from pyiceberg.table import Table as IcebergTable
from pyiceberg.types import (
    IntegerType,
    ListType,
    MapType,
    NestedField,
    StringType,
    TimestamptzType,
)

from .window_summary import WindowSummary

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
WINDOW_STATUS_ARROW_SCHEMA: pa.Schema = schema_to_pyarrow(WINDOW_STATUS_SCHEMA)


class WindowStore:
    """Persists harvesting window status rows in an Apache Iceberg table."""

    def __init__(self, table: IcebergTable) -> None:
        self.table = table
        self._lock = Lock()

    def list_in_range(
        self, start_time: datetime | None = None, end_time: datetime | None = None
    ) -> list[WindowSummary]:
        """Return rows within the given time range."""
        expr: BooleanExpression = ALWAYS_TRUE
        if start_time:
            expr = And(expr, GreaterThanOrEqual("window_start", start_time))
        if end_time:
            expr = And(expr, LessThan("window_start", end_time))

        arrow_table = self.table.scan().filter(expr).to_arrow()
        pylist = arrow_table.to_pylist(maps_as_pydicts="lossy")
        return [WindowSummary.model_validate(row) for row in pylist]

    def list_by_keys(self, keys: list[str]) -> list[WindowSummary]:
        """Return rows whose window_key is in the given list."""
        if not keys:
            return []
        arrow_table = self.table.scan().filter(In("window_key", keys)).to_arrow()
        pylist = arrow_table.to_pylist(maps_as_pydicts="lossy")
        return [WindowSummary.model_validate(row) for row in pylist]

    def load_status_map(
        self,
        start_time: datetime | None = None,
        end_time: datetime | None = None,
    ) -> dict[str, WindowSummary]:
        """Load window summaries keyed by their window identifier.

        Args:
            start_time: If given, only include windows with ``window_start >= start_time``.
            end_time: If given, only include windows with ``window_start < end_time``.
        """
        rows = self.list_in_range(start_time, end_time)
        return {row.window_key: row for row in rows}

    def upsert(self, record: WindowSummary) -> None:
        """Replace any existing row for this window, in a single Iceberg commit."""
        self.upsert_many([record])

    def upsert_many(self, records: list[WindowSummary]) -> None:
        """Replace any existing rows for these windows, in a single Iceberg commit."""
        if not records:
            return
        arrow = pa.Table.from_pylist(
            [record.model_dump() for record in records],
            schema=WINDOW_STATUS_ARROW_SCHEMA,
        )
        keys = [str(record.window_key) for record in records]
        with self._lock, self.table.transaction() as tx:
            tx.overwrite(arrow, overwrite_filter=In("window_key", keys))

    def list_by_state(self, state: str) -> list[dict[str, Any]]:
        """Return rows filtered by state (e.g. success, failed)."""
        scan = self.table.scan().filter(EqualTo("state", state))
        arrow_table = scan.to_arrow()
        return arrow_table.to_pylist(maps_as_pydicts="lossy")
