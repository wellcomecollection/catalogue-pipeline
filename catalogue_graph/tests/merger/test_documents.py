from datetime import UTC, datetime
from pathlib import Path

import polars as pl
import pytest

from merger import documents
from merger.documents import _groups, _read_documents


def _write(path: Path, rows: list[tuple[str, int, datetime, str]]) -> str:
    frame = pl.DataFrame(
        rows,
        schema={
            "id": pl.Utf8,
            "version": pl.Int64,
            "last_modified": pl.Datetime("us", "UTC"),
            "content": pl.Utf8,
        },
        orient="row",
    ).sort("id")
    frame.write_parquet(path)
    return str(path)


def test_read_documents_returns_the_current_row_of_each_wanted_work(
    tmp_path: Path,
) -> None:
    earlier = datetime(2026, 11, 1, tzinfo=UTC)
    later = datetime(2026, 11, 2, tzinfo=UTC)
    files = [
        _write(
            tmp_path / "a.parquet",
            [
                ("aaaaaaaa", 1, earlier, "a v1"),
                ("bbbbbbbb", 1, earlier, "b v1 early"),
                ("cccccccc", 1, earlier, "c unwanted"),
            ],
        ),
        _write(
            tmp_path / "b.parquet",
            [
                ("aaaaaaaa", 2, earlier, "a v2"),
                ("bbbbbbbb", 1, later, "b v1 late"),
            ],
        ),
    ]

    found = _read_documents(files, {"aaaaaaaa", "bbbbbbbb", "dddddddd"})

    assert found == {"aaaaaaaa": "a v2", "bbbbbbbb": "b v1 late"}


def test_groups_keep_components_whole(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(documents, "GROUP_SIZE", 3)
    components = [["a", "b"], ["c", "d"], ["e"], ["f", "g", "h", "i"], ["j"]]

    groups = list(_groups(components))

    assert groups == [[["a", "b"], ["c", "d"]], [["e"], ["f", "g", "h", "i"]], [["j"]]]
