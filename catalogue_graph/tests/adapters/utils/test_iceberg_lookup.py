from datetime import UTC, datetime
from pathlib import Path

import polars as pl
import pyarrow as pa
import pytest
from pyiceberg.catalog.sql import SqlCatalog

from adapters.utils.iceberg_lookup import IcebergTableLookup

EARLIER = datetime(2026, 11, 1, tzinfo=UTC)
LATER = datetime(2026, 11, 2, tzinfo=UTC)
SCHEMA: dict[str, pl.DataType] = {
    "id": pl.String(),
    "version": pl.Int64(),
    "last_modified": pl.Datetime("us", "UTC"),
    "content": pl.String(),
}


def _write(path: Path, rows: list[tuple[str, int, datetime, str]]) -> str:
    pl.DataFrame(rows, schema=SCHEMA, orient="row").sort("id").write_parquet(path)
    return str(path)


@pytest.fixture
def files(tmp_path: Path) -> list[str]:
    return [
        _write(
            tmp_path / "a.parquet",
            [
                ("aaaaaaaa", 1, EARLIER, "a v1"),
                ("bbbbbbbb", 1, EARLIER, "b v1 early"),
                ("cccccccc", 1, EARLIER, "c"),
            ],
        ),
        _write(
            tmp_path / "b.parquet",
            [("aaaaaaaa", 2, EARLIER, "a v2"), ("bbbbbbbb", 1, LATER, "b v1 late")],
        ),
    ]


def test_fetch_returns_every_row_of_the_wanted_keys(files: list[str]) -> None:
    rows = IcebergTableLookup(files, key="id").fetch(
        ["aaaaaaaa", "dddddddd"], columns=("id", "content")
    )

    assert rows.columns == ["id", "content"]
    assert sorted(rows["content"]) == ["a v1", "a v2"]


def test_fetch_with_latest_by_keeps_one_row_per_key(files: list[str]) -> None:
    rows = IcebergTableLookup(files, key="id").fetch(
        {"aaaaaaaa", "bbbbbbbb"},
        columns=("id", "content"),
        latest_by=("version", "last_modified"),
    )

    assert dict(zip(rows["id"], rows["content"], strict=True)) == {
        "aaaaaaaa": "a v2",
        "bbbbbbbb": "b v1 late",
    }


def test_for_snapshot_reads_the_pinned_snapshot(tmp_path: Path) -> None:
    catalog = SqlCatalog(
        "test", uri=f"sqlite:///{tmp_path}/catalog.db", warehouse=f"file://{tmp_path}"
    )
    catalog.create_namespace("ns")
    arrow = pa.table({"id": ["aaaaaaaa"], "content": ["first"]})
    table = catalog.create_table("ns.t", schema=arrow.schema)
    table.append(arrow)
    snapshot = table.current_snapshot()
    assert snapshot is not None
    first = snapshot.snapshot_id
    table.append(pa.table({"id": ["bbbbbbbb"], "content": ["second"]}))

    pinned = IcebergTableLookup.for_snapshot(table, key="id", snapshot_id=first)
    latest = IcebergTableLookup.for_snapshot(table, key="id")

    keys = ["aaaaaaaa", "bbbbbbbb"]
    assert pinned.fetch(keys, columns=("content",))["content"].to_list() == ["first"]
    assert sorted(latest.fetch(keys, columns=("content",))["content"]) == [
        "first",
        "second",
    ]


def test_for_snapshot_rejects_an_unknown_snapshot(tmp_path: Path) -> None:
    catalog = SqlCatalog(
        "test", uri=f"sqlite:///{tmp_path}/catalog.db", warehouse=f"file://{tmp_path}"
    )
    catalog.create_namespace("ns")
    table = catalog.create_table(
        "ns.t", schema=pa.table({"id": ["aaaaaaaa"], "content": ["x"]}).schema
    )

    with pytest.raises(ValueError, match="no snapshot 123"):
        IcebergTableLookup.for_snapshot(table, key="id", snapshot_id=123)


def test_for_snapshot_rejects_a_table_without_data(tmp_path: Path) -> None:
    catalog = SqlCatalog(
        "test", uri=f"sqlite:///{tmp_path}/catalog.db", warehouse=f"file://{tmp_path}"
    )
    catalog.create_namespace("ns")
    table = catalog.create_table(
        "ns.t", schema=pa.table({"id": ["aaaaaaaa"], "content": ["x"]}).schema
    )

    with pytest.raises(ValueError, match="no data files"):
        IcebergTableLookup.for_snapshot(table, key="id")
