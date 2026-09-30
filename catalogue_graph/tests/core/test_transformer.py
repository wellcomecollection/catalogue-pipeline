"""Tests for how BatchTransformer reports errors across the transform and write stages."""

from collections.abc import Generator, Iterable
from typing import Any

from core.document import Document
from core.sinks import WriteResult
from core.source import BaseSource
from core.transformer import MAX_ERRORS, BatchTransformer
from tests.mocks import ListSink


class _ListSource(BaseSource):
    def __init__(self, rows: list[str]):
        self.rows = rows

    def stream_raw(self) -> Generator[str]:
        yield from self.rows


class _RowTransformer(BatchTransformer):
    """Rows starting with `bad` fail to transform; every other row becomes one document."""

    def __init__(self, rows: list[str]):
        super().__init__()
        self.source = _ListSource(rows)

    def transform(self, raw_nodes: Iterable[Any]) -> Generator[Document]:
        for row_id in raw_nodes:
            if row_id.startswith("bad"):
                self._add_error(ValueError("no good"), "transform", row_id)
                continue
            yield Document(source_id=row_id, target_id=row_id, body={})


class _RejectingSink:
    """Fails every document it is given."""

    def write(self, documents: list[Document]) -> WriteResult:
        return WriteResult(failed=[(d, {"reason": "rejected"}) for d in documents])


class TestErrors:
    def test_errors_are_capped_across_transform_and_write_stages(self) -> None:
        rows = [f"bad{i}" for i in range(1_500)] + [f"row{i}" for i in range(1_500)]

        result = _RowTransformer(rows).stream_to(_RejectingSink())

        assert len(result.errors) == MAX_ERRORS
        assert [e.stage for e in result.errors[:MAX_ERRORS]] == [
            "transform"
        ] * MAX_ERRORS
        # Every rejected document is still counted, whatever the cap on errors.
        assert len(result.failed_ids) == 1_500

    def test_transform_errors_reach_every_sink(self) -> None:
        accepting = ListSink()

        accepted, rejected = _RowTransformer(["bad1", "row1"]).stream_to_many(
            accepting, _RejectingSink()
        )

        assert accepted.accepted_ids == ["row1"]
        assert [(e.row_id, e.stage) for e in accepted.errors] == [("bad1", "transform")]
        assert rejected.failed_ids == ["row1"]
        assert [(e.row_id, e.stage) for e in rejected.errors] == [
            ("bad1", "transform"),
            ("row1", "index"),
        ]
