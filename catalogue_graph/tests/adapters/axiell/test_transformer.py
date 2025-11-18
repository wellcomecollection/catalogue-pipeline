from __future__ import annotations

from datetime import UTC, datetime
from typing import Any, cast

import pyarrow as pa
import pytest
from elasticsearch import Elasticsearch

from adapters.axiell.models import AxiellAdapterTransformerEvent
from adapters.axiell.steps import transformer
from adapters.utils.iceberg import IcebergTableClient


class StubTableClient:
    def __init__(self, table: pa.Table) -> None:
        self.table = table
        self.requested_changeset: str | None = None

    def get_records_by_changeset(self, changeset_id: str) -> pa.Table:
        self.requested_changeset = changeset_id
        return self.table


class StubElasticsearch:
    pass


def _table(rows: list[dict[str, Any]]) -> pa.Table:
    return pa.Table.from_pylist(rows)


def _runtime_with(
    table_client: StubTableClient,
    *,
    index_name: str = "axiell-test",
) -> transformer.TransformerRuntime:
    return transformer.TransformerRuntime(
        table_client=cast(IcebergTableClient, table_client),
        es_client=cast(Elasticsearch, StubElasticsearch()),
        index_name=index_name,
    )


def _request() -> AxiellAdapterTransformerEvent:
    return AxiellAdapterTransformerEvent(changeset_id="changeset-1", job_id="job-abc")


def test_execute_transform_indexes_documents(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    rows = [
        {
            "namespace": "axiell",
            "id": "a1",
            "content": "<xml />",
            "last_modified": datetime.now(tz=UTC),
        },
        {
            "namespace": "axiell",
            "id": "a2",
            "content": "<xml />",
            "last_modified": datetime.now(tz=UTC),
        },
    ]
    table_client = StubTableClient(_table(rows))
    runtime = _runtime_with(table_client)

    captured_actions: list[dict[str, Any]] = []

    def fake_bulk(client: Any, actions: Any, **kwargs: Any) -> tuple[int, list[Any]]:
        captured_actions[:] = list(actions)
        return len(captured_actions), []

    monkeypatch.setattr(transformer.elasticsearch.helpers, "bulk", fake_bulk)

    result = transformer.execute_transform(_request(), runtime=runtime)

    assert result.changeset_id == "changeset-1"
    assert result.indexed == 2
    assert result.errors == []
    assert result.job_id == "job-abc"
    assert table_client.requested_changeset == "changeset-1"
    assert captured_actions == [
        {
            "_index": "axiell-test",
            "_id": "a1",
            "_source": transformer._dummy_document(rows[0]),
        },
        {
            "_index": "axiell-test",
            "_id": "a2",
            "_source": transformer._dummy_document(rows[1]),
        },
    ]


def test_execute_transform_skips_when_no_rows(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    table_client = StubTableClient(_table([]))
    runtime = _runtime_with(table_client)

    def fake_bulk(*args: Any, **kwargs: Any) -> None:  # noqa: ARG001
        raise AssertionError("bulk should not be called when there are no documents")

    monkeypatch.setattr(transformer.elasticsearch.helpers, "bulk", fake_bulk)

    result = transformer.execute_transform(_request(), runtime=runtime)

    assert result.indexed == 0
    assert result.errors == []
    assert result.job_id == "job-abc"


def test_execute_transform_surfaces_errors(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    rows = [{"namespace": "axiell", "id": "ax-1", "content": "<xml />"}]
    table_client = StubTableClient(_table(rows))
    runtime = _runtime_with(table_client)

    def fake_bulk(
        client: Any, actions: Any, **kwargs: Any
    ) -> tuple[int, list[dict[str, Any]]]:
        return 0, [{"index": {"_id": "ax-1", "status": 500}}]

    monkeypatch.setattr(transformer.elasticsearch.helpers, "bulk", fake_bulk)

    result = transformer.execute_transform(_request(), runtime=runtime)

    assert result.errors == ["id=ax-1 status=500"]
    assert result.indexed == 0
    assert result.job_id == "job-abc"
