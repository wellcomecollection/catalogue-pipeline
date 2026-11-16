from datetime import UTC, datetime
from pathlib import Path
from unittest.mock import MagicMock

import polars as pl
import pytest

from adapters.utils.iceberg_lookup import IcebergTableLookup
from merger import documents
from merger.components import COMPONENTS_SCHEMA
from merger.documents import _groups, _read_documents, fetch_components


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


def test_groups_keep_components_whole(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(documents, "GROUP_SIZE", 3)
    components = [["a", "b"], ["c", "d"], ["e"], ["f", "g", "h", "i"], ["j"]]

    groups = list(_groups(components))

    assert groups == [[["a", "b"], ["c", "d"]], [["e"], ["f", "g", "h", "i"]], [["j"]]]
