"""Reading a group's documents from the table, and the works of each component."""

from datetime import UTC, datetime
from pathlib import Path
from unittest.mock import MagicMock

import polars as pl
import pytest

from adapters.utils.iceberg_lookup import IcebergTableLookup
from merger.documents import read_documents, take_works
from tests.merger.works import sierra_work


def test_read_documents_maps_each_wanted_work_to_its_current_document(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    earlier = datetime(2026, 11, 1, tzinfo=UTC)
    later = datetime(2026, 11, 2, tzinfo=UTC)
    pl.DataFrame(
        [
            ("aaaaaaaa", 1, earlier, "a v1"),
            ("aaaaaaaa", 2, earlier, "a v2"),
            ("bbbbbbbb", 1, later, "b v1 late"),
            ("bbbbbbbb", 1, earlier, "b v1 early"),
            ("cccccccc", 1, earlier, "c unwanted"),
        ],
        schema={
            "id": pl.Utf8,
            "version": pl.Int64,
            "last_modified": pl.Datetime("us", "UTC"),
            "content": pl.Utf8,
        },
        orient="row",
    ).write_parquet(tmp_path / "works.parquet")
    lookup = IcebergTableLookup([str(tmp_path / "works.parquet")], key="id")
    monkeypatch.setattr(
        IcebergTableLookup, "for_snapshot", MagicMock(return_value=lookup)
    )

    found = read_documents(MagicMock(), 7, {"aaaaaaaa", "bbbbbbbb", "dddddddd"})

    assert found == {"aaaaaaaa": "a v2", "bbbbbbbb": "b v1 late"}


def test_read_documents_for_no_works_reads_nothing() -> None:
    table = MagicMock()

    assert read_documents(table, 7, set()) == {}
    table.scan.assert_not_called()


def test_take_works_parses_the_works_and_drops_their_documents() -> None:
    a, b, c = sierra_work(), sierra_work(), sierra_work()
    documents = {w.state.canonical_id: w.model_dump_json() for w in (a, b, c)}

    works = take_works(documents, [a.state.canonical_id, b.state.canonical_id])

    assert works == [a, b]
    assert list(documents) == [c.state.canonical_id]


def test_take_works_raises_when_a_work_is_missing() -> None:
    a = sierra_work()
    documents = {a.state.canonical_id: a.model_dump_json()}

    with pytest.raises(ValueError, match="missing from the table: \\['gone'\\]"):
        take_works(documents, [a.state.canonical_id, "gone"])
