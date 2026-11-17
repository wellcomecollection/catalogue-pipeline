from datetime import UTC, datetime
from pathlib import Path
from unittest.mock import MagicMock

import polars as pl
import pytest

from adapters.utils.iceberg_lookup import IcebergTableLookup
from merger import documents
from merger.components import COMPONENTS_SCHEMA
from merger.documents import _read_documents, fetch_components
from tests.merger.works import sierra_work


def test_read_documents_maps_each_wanted_work_to_its_current_document(
    tmp_path: Path,
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

    found = _read_documents(lookup, {"aaaaaaaa", "bbbbbbbb", "dddddddd"})

    assert found == {"aaaaaaaa": "a v2", "bbbbbbbb": "b v1 late"}


def test_fetch_components_with_nothing_changed_reads_nothing() -> None:
    table = MagicMock()

    works = list(fetch_components(table, 7, pl.DataFrame(schema=COMPONENTS_SCHEMA)))

    assert works == []
    table.scan.assert_not_called()


def test_fetch_components_yields_each_component_with_the_works_found(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    a, b, c = sierra_work(), sierra_work(), sierra_work()
    content = {w.state.canonical_id: w.model_dump_json() for w in (a, b, c)}
    changed = pl.DataFrame(
        {
            "id": [
                a.state.canonical_id,
                b.state.canonical_id,
                c.state.canonical_id,
                "gone",
            ],
            "component_id": ["1", "1", "2", "2"],
        }
    )
    reads: list[set[str]] = []

    def read(lookup: IcebergTableLookup, wanted: set[str]) -> dict[str, str]:
        reads.append(wanted)
        return {i: content[i] for i in wanted if i in content}

    monkeypatch.setattr(documents, "_read_documents", read)
    monkeypatch.setattr(IcebergTableLookup, "for_snapshot", MagicMock())

    fetched = list(fetch_components(MagicMock(), 7, changed))

    assert len(reads) == 1
    assert fetched == [
        ([a.state.canonical_id, b.state.canonical_id], [a, b]),
        ([c.state.canonical_id, "gone"], [c]),
    ]


def test_fetch_components_drops_each_document_once_parsed(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    a, b = sierra_work(), sierra_work()
    read = {w.state.canonical_id: w.model_dump_json() for w in (a, b)}
    changed = pl.DataFrame(
        {"id": [a.state.canonical_id, b.state.canonical_id], "component_id": ["1", "2"]}
    )
    monkeypatch.setattr(documents, "_read_documents", lambda lookup, wanted: read)
    monkeypatch.setattr(IcebergTableLookup, "for_snapshot", MagicMock())

    fetched = fetch_components(MagicMock(), 7, changed)

    next(fetched)
    assert list(read) == [b.state.canonical_id]
    next(fetched)
    assert read == {}
