from __future__ import annotations

from datetime import UTC, datetime

import pyarrow as pa
import pytest

from adapters.axiell.models import TransformRequest
from adapters.axiell.steps import transformer


class StubTableClient:
    def __init__(self, table: pa.Table) -> None:
        self.table = table
        self.requested_changeset: str | None = None

    def get_records_by_changeset(self, changeset_id: str) -> pa.Table:
        self.requested_changeset = changeset_id
        return self.table


def _table(rows: list[dict]) -> pa.Table:
    return pa.Table.from_pylist(rows)


def _request() -> TransformRequest:
    return TransformRequest(changeset_id="changeset-1")


def test_execute_transform_indexes_documents(monkeypatch):
    rows = [
        {"namespace": "axiell", "id": "a1", "content": "<xml />", "last_modified": datetime.now(tz=UTC)},
        {"namespace": "axiell", "id": "a2", "content": "<xml />", "last_modified": datetime.now(tz=UTC)},
    ]
    table_client = StubTableClient(_table(rows))
    runtime = transformer.TransformerRuntime(
        table_client=table_client,
        es_client=None,  # type: ignore[arg-type]
        index_name="axiell-test",
    )

    def fake_bulk(client, actions, **kwargs):  # noqa: ANN001
        fake_bulk.actions = list(actions)
        return len(fake_bulk.actions), []

    fake_bulk.actions = []  # type: ignore[attr-defined]
    monkeypatch.setattr(transformer.elasticsearch.helpers, "bulk", fake_bulk)

    result = transformer.execute_transform(_request(), runtime=runtime)

    assert result.changeset_id == "changeset-1"
    assert result.indexed == 2
    assert result.errors == []
    assert table_client.requested_changeset == "changeset-1"
    assert fake_bulk.actions == [
        {"_index": "axiell-test", "_id": "a1", "_source": transformer._dummy_document(rows[0])},
        {"_index": "axiell-test", "_id": "a2", "_source": transformer._dummy_document(rows[1])},
    ]


def test_execute_transform_skips_when_no_rows(monkeypatch):
    table_client = StubTableClient(_table([]))
    runtime = transformer.TransformerRuntime(
        table_client=table_client,
        es_client=None,  # type: ignore[arg-type]
        index_name="axiell-test",
    )

    def fake_bulk(*args, **kwargs):  # noqa: ANN001, ARG001
        raise AssertionError("bulk should not be called when there are no documents")

    monkeypatch.setattr(transformer.elasticsearch.helpers, "bulk", fake_bulk)

    result = transformer.execute_transform(_request(), runtime=runtime)

    assert result.indexed == 0
    assert result.errors == []


def test_execute_transform_surfaces_errors(monkeypatch):
    rows = [{"namespace": "axiell", "id": "ax-1", "content": "<xml />"}]
    table_client = StubTableClient(_table(rows))
    runtime = transformer.TransformerRuntime(
        table_client=table_client,
        es_client=None,  # type: ignore[arg-type]
        index_name="axiell-test",
    )

    def fake_bulk(client, actions, **kwargs):  # noqa: ANN001
        return 0, [{"index": {"_id": "ax-1", "status": 500}}]

    monkeypatch.setattr(transformer.elasticsearch.helpers, "bulk", fake_bulk)

    result = transformer.execute_transform(_request(), runtime=runtime)

    assert result.errors == ["id=ax-1 status=500"]
    assert result.indexed == 0
