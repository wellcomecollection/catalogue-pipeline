from __future__ import annotations

from collections.abc import Callable, Generator
from datetime import UTC, datetime
from typing import Any
from uuid import uuid1

import pyarrow as pa
import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.utils.adapter_store import AdapterStore
from adapters.utils.iceberg import get_local_table
from adapters.utils.schemata import ARROW_SCHEMA
from adapters.utils.window_store import WINDOW_STATUS_SCHEMA

# Type alias for the factory fixture
AdapterStoreFactory = Callable[[list[dict]], AdapterStore]


def records_to_table(
    records: list[dict[str, Any]],
    namespace: str = "test_namespace",
    add_timestamp: bool = False,
) -> pa.Table:
    """Create an Arrow table with the repo-standard schema from a list of dicts.

    Provides sensible defaults for required fields:
    - namespace: "test_namespace" (or as specified)
    - last_modified: None by default, or datetime.now(UTC) if add_timestamp=True
    - deleted: None

    Args:
        records: List of dicts with at minimum 'id' and 'content' keys
        namespace: Default namespace to apply to records without one
        add_timestamp: If True, set last_modified to now (UTC) for records without one

    Usage:
        table = records_to_table([
            {"id": "rec001", "content": "hello"},
            {"id": "rec002", "content": "world", "deleted": True},
        ])
    """
    data: list[dict[str, Any]] = []
    for item in records:
        new_item = item.copy()
        new_item.setdefault("namespace", namespace)
        new_item.setdefault("deleted", None)
        if add_timestamp:
            new_item.setdefault("last_modified", datetime.now(UTC))
        else:
            new_item.setdefault("last_modified", None)
        data.append(new_item)

    return pa.Table.from_pylist(data, schema=ARROW_SCHEMA)


@pytest.fixture
def adapter_store_with_records(
    temporary_table: IcebergTable,
) -> AdapterStoreFactory:
    """Factory fixture that creates an AdapterStore seeded with records.

    Provides sensible defaults for required fields:
    - namespace: "test_namespace"
    - last_modified: None
    - deleted: None

    Usage:
        def test_something(adapter_store_with_records):
            store = adapter_store_with_records([
                {"id": "rec001", "content": "hello"},
                {"id": "rec002", "content": "world", "deleted": True},
            ])
            result = store.get_all_records()
    """

    def _factory(
        records: list[dict], namespace: str = "test_namespace"
    ) -> AdapterStore:
        if records:
            table = records_to_table(records, namespace=namespace)
            temporary_table.append(table)
        return AdapterStore(temporary_table)

    return _factory


@pytest.fixture
def temporary_table() -> Generator[IcebergTable, None, None]:
    table_name = str(uuid1())
    table = get_local_table(
        table_name=table_name,
        namespace="test",
        db_name="test_catalog",
    )
    try:
        yield table
    finally:
        table.catalog.drop_table(f"test.{table_name}")


@pytest.fixture
def temporary_window_status_table() -> Generator[IcebergTable, None, None]:
    table_name = str(uuid1())
    table = get_local_table(
        table_name=table_name,
        namespace="test",
        db_name="test_catalog",
        schema=WINDOW_STATUS_SCHEMA,
    )
    try:
        yield table
    finally:
        table.catalog.drop_table(f"test.{table_name}")
