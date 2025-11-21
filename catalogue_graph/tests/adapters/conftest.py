from __future__ import annotations

from collections.abc import Generator
from uuid import uuid1

import pytest
from pyiceberg.table import Table as IcebergTable

from adapters.ebsco.table_config import get_local_table
from adapters.utils.window_store import WINDOW_STATUS_SCHEMA


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
