from collections.abc import Sequence
from contextlib import suppress
from datetime import UTC, datetime, timedelta
from pathlib import Path
from uuid import uuid4

from pyiceberg.catalog.sql import SqlCatalog
from pyiceberg.exceptions import NamespaceAlreadyExistsError
from pyiceberg.table import Table as IcebergTable

from adapters.utils.window_store import (
    WINDOW_STATUS_SCHEMA,
    WindowStore,
)
from adapters.utils.window_summary import WindowState, WindowSummary
from models.incremental_window import IncrementalWindow


def _create_table(
    catalog_uri: str,
    warehouse_path: Path,
    namespace: str | Sequence[str],
    table_name: str,
    catalog_name: str,
) -> IcebergTable:
    warehouse_path.mkdir(parents=True, exist_ok=True)
    catalog = SqlCatalog(
        name=catalog_name,
        uri=catalog_uri,
        warehouse=str(warehouse_path),
    )
    namespace_tuple = (namespace,) if isinstance(namespace, str) else tuple(namespace)
    with suppress(NamespaceAlreadyExistsError):
        catalog.create_namespace(namespace_tuple)
    identifier = (*namespace_tuple, table_name)
    if catalog.table_exists(identifier):
        return catalog.load_table(identifier)
    return catalog.create_table(
        identifier=identifier,
        schema=WINDOW_STATUS_SCHEMA,
    )


def test_window_store_round_trip(tmp_path: Path) -> None:
    catalog_path = tmp_path / "catalog.db"
    warehouse_path = tmp_path / "warehouse"
    catalog_uri = f"sqlite:///{catalog_path}"
    table = _create_table(
        catalog_uri=catalog_uri,
        warehouse_path=warehouse_path,
        namespace="harvest",
        table_name=f"window_status_{uuid4().hex}",
        catalog_name=f"catalog_{uuid4().hex}",
    )
    store = WindowStore(table)

    assert store.load_status_map() == {}

    start = datetime(2025, 11, 14, 6, 0, tzinfo=UTC)
    end = datetime(2025, 11, 14, 6, 15, tzinfo=UTC)
    record = WindowSummary(
        window_start=start,
        window_end=end,
        state="success",
        attempts=1,
        last_error=None,
        record_ids=["id:1", "id:2", "id:3"],
        updated_at=datetime.now(UTC),
        tags=None,
    )

    store.upsert(record)
    stored = store.load_status_map()
    assert record.window_key in stored
    assert stored[record.window_key].state == "success"
    assert stored[record.window_key].record_ids == ["id:1", "id:2", "id:3"]

    # Updating the same window should overwrite the prior row
    updated_record = WindowSummary(
        window_start=start,
        window_end=end,
        state="failed",
        attempts=3,
        last_error="Timeout",
        record_ids=[],
        updated_at=datetime.now(UTC),
        tags=None,
    )
    store.upsert(updated_record)

    stored_again = store.load_status_map()
    assert stored_again[record.window_key].state == "failed"
    assert stored_again[record.window_key].attempts == 3
    assert stored_again[record.window_key].last_error == "Timeout"
    assert stored_again[record.window_key].record_ids == []

    failed_rows = store.list_by_state("failed")
    assert len(failed_rows) == 1
    assert failed_rows[0]["window_key"] == record.window_key
    assert failed_rows[0]["record_ids"] == []


def test_window_store_upsert_many(tmp_path: Path) -> None:
    catalog_path = tmp_path / "catalog.db"
    warehouse_path = tmp_path / "warehouse"
    table = _create_table(
        catalog_uri=f"sqlite:///{catalog_path}",
        warehouse_path=warehouse_path,
        namespace="harvest",
        table_name=f"window_status_{uuid4().hex}",
        catalog_name=f"catalog_{uuid4().hex}",
    )
    store = WindowStore(table)

    def make_record(start: datetime, state: WindowState = "success") -> WindowSummary:
        return WindowSummary(
            window_start=start,
            window_end=start + timedelta(minutes=15),
            state=state,
            attempts=1,
            last_error=None,
            record_ids=[],
            updated_at=datetime.now(UTC),
            tags=None,
        )

    t1 = datetime(2025, 1, 1, 10, 0, tzinfo=UTC)
    t2 = datetime(2025, 1, 1, 10, 15, tzinfo=UTC)
    t3 = datetime(2025, 1, 1, 10, 30, tzinfo=UTC)
    store.upsert(make_record(t1))
    store.upsert(make_record(t2))

    # Replaces existing rows and inserts new ones in one call
    replaced = [
        make_record(t1, state="failed"),
        make_record(t2, state="failed"),
        make_record(t3),
    ]
    store.upsert_many(replaced)

    stored = store.load_status_map()
    assert len(stored) == 3
    assert stored[make_record(t1).window_key].state == "failed"
    assert stored[make_record(t2).window_key].state == "failed"
    assert stored[make_record(t3).window_key].state == "success"

    # Empty input is a no-op (no new snapshot)
    snapshots_after = len(list(store.table.snapshots()))
    store.upsert_many([])
    assert len(list(store.table.snapshots())) == snapshots_after

    # list_by_keys returns exactly the named rows; unknown keys are ignored
    keys = [make_record(t1).window_key, make_record(t3).window_key]
    rows = store.list_by_keys([*keys, "2099-01-01T00:00:00+00:00/PT15M"])
    assert sorted(row.window_key for row in rows) == sorted(keys)
    assert store.list_by_keys([]) == []


def test_window_store_list_in_range(tmp_path: Path) -> None:
    catalog_path = tmp_path / "catalog.db"
    warehouse_path = tmp_path / "warehouse"
    catalog_uri = f"sqlite:///{catalog_path}"
    table = _create_table(
        catalog_uri=catalog_uri,
        warehouse_path=warehouse_path,
        namespace="harvest",
        table_name=f"window_status_{uuid4().hex}",
        catalog_name=f"catalog_{uuid4().hex}",
    )
    store = WindowStore(table)

    # Create 3 records at different times
    t1 = datetime(2025, 1, 1, 10, 0, tzinfo=UTC)
    t2 = datetime(2025, 1, 1, 11, 0, tzinfo=UTC)
    t3 = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)

    for t in [t1, t2, t3]:
        store.upsert(
            WindowSummary(
                window_start=t,
                window_end=t + timedelta(minutes=15),
                state="success",
                attempts=1,
                last_error=None,
                record_ids=[],
                updated_at=datetime.now(UTC),
                tags=None,
            )
        )

    # Test range queries
    # All
    assert (
        len(store.list_in_range(start_time=t1, end_time=t3 + timedelta(hours=1))) == 3
    )

    # Start filter
    assert len(store.list_in_range(start_time=t2)) == 2  # t2, t3

    # End filter
    assert len(store.list_in_range(end_time=t2)) == 1  # t1 (end is exclusive)

    # Both
    assert len(store.list_in_range(start_time=t2, end_time=t3)) == 1  # t2


def test_load_status_map_filters_by_time_range(tmp_path: Path) -> None:
    catalog_path = tmp_path / "catalog.db"
    warehouse_path = tmp_path / "warehouse"
    catalog_uri = f"sqlite:///{catalog_path}"
    table = _create_table(
        catalog_uri=catalog_uri,
        warehouse_path=warehouse_path,
        namespace="harvest",
        table_name=f"window_status_{uuid4().hex}",
        catalog_name=f"catalog_{uuid4().hex}",
    )
    store = WindowStore(table)

    t1 = datetime(2025, 1, 1, 10, 0, tzinfo=UTC)
    t2 = datetime(2025, 1, 1, 11, 0, tzinfo=UTC)
    t3 = datetime(2025, 1, 1, 12, 0, tzinfo=UTC)

    for t in [t1, t2, t3]:
        store.upsert(
            WindowSummary(
                window_start=t,
                window_end=t + timedelta(minutes=15),
                state="success",
                attempts=1,
                last_error=None,
                record_ids=["a", "b"],
                updated_at=datetime.now(UTC),
                tags=None,
            )
        )

    # No filters → returns all
    assert len(store.load_status_map()) == 3

    # Start filter only
    assert len(store.load_status_map(start_time=t2)) == 2  # t2, t3

    # End filter only
    assert len(store.load_status_map(end_time=t2)) == 1  # t1

    # Both filters
    result = store.load_status_map(start_time=t2, end_time=t3)
    assert len(result) == 1  # t2 only
    assert (
        IncrementalWindow(
            start_time=t2, end_time=t2 + timedelta(minutes=15)
        ).to_iso_string()
        in result
    )
