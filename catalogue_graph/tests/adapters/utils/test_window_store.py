from collections.abc import Sequence
from contextlib import suppress
from datetime import UTC, datetime
from pathlib import Path
from uuid import uuid4

from pyiceberg.catalog.sql import SqlCatalog
from pyiceberg.exceptions import NamespaceAlreadyExistsError
from pyiceberg.table import Table as IcebergTable

from adapters.utils.window_store import (
    WINDOW_STATUS_PARTITION_SPEC,
    WINDOW_STATUS_SCHEMA,
    WindowStatusRecord,
    WindowStore,
)


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
        partition_spec=WINDOW_STATUS_PARTITION_SPEC,
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
    record = WindowStatusRecord(
        window_key="2025-11-14T06:00Z_2025-11-14T06:15Z",
        window_start=start,
        window_end=end,
        state="success",
        attempts=1,
        last_error=None,
        record_ids=("id:1", "id:2", "id:3"),
        updated_at=datetime.now(UTC),
    )

    store.upsert(record)
    stored = store.load_status_map()
    assert record.window_key in stored
    assert stored[record.window_key]["state"] == "success"
    assert stored[record.window_key]["record_ids"] == ["id:1", "id:2", "id:3"]

    # Updating the same window should overwrite the prior row
    updated_record = WindowStatusRecord(
        window_key=record.window_key,
        window_start=start,
        window_end=end,
        state="failed",
        attempts=3,
        last_error="Timeout",
        record_ids=(),
        updated_at=datetime.now(UTC),
    )
    store.upsert(updated_record)

    stored_again = store.load_status_map()
    assert stored_again[record.window_key]["state"] == "failed"
    assert stored_again[record.window_key]["attempts"] == 3
    assert stored_again[record.window_key]["last_error"] == "Timeout"
    assert stored_again[record.window_key]["record_ids"] == []

    failed_rows = store.list_by_state("failed")
    assert len(failed_rows) == 1
    assert failed_rows[0]["window_key"] == record.window_key
    assert failed_rows[0]["record_ids"] == []
