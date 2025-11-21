from __future__ import annotations

from typing import Any, cast

import pyarrow as pa
import pytest
from elasticsearch import Elasticsearch
from pyiceberg.table import Table as IcebergTable

from adapters.axiell.models.step_events import AxiellAdapterTransformerEvent
from adapters.axiell.steps import transformer
from adapters.axiell.steps.loader import AXIELL_NAMESPACE
from adapters.utils.iceberg import IcebergTableClient
from adapters.utils.schemata import ARROW_SCHEMA


class StubElasticsearch:
    pass


def _runtime_with(
    table_client: IcebergTableClient,
    *,
    index_name: str = "axiell-test",
) -> transformer.TransformerRuntime:
    return transformer.TransformerRuntime(
        table_client=table_client,
        es_client=cast(Elasticsearch, StubElasticsearch()),
        index_name=index_name,
    )


def _seed_changeset(
    table_client: IcebergTableClient, rows: list[dict[str, Any]]
) -> str:
    table = pa.Table.from_pylist(rows, schema=ARROW_SCHEMA)
    changeset_id = table_client.incremental_update(
        table, record_namespace=AXIELL_NAMESPACE
    )
    assert changeset_id is not None
    return changeset_id


def _request() -> AxiellAdapterTransformerEvent:
    return AxiellAdapterTransformerEvent(
        changeset_ids=["changeset-1"], job_id="job-abc"
    )


def test_execute_transform_indexes_documents(
    monkeypatch: pytest.MonkeyPatch, temporary_table: IcebergTable
) -> None:
    rows = [
        {
            "namespace": "axiell",
            "id": "a1",
            "content": "<xml />",
        },
        {
            "namespace": "axiell",
            "id": "a2",
            "content": "<xml />",
        },
    ]

    table_client = IcebergTableClient(
        temporary_table, default_namespace=AXIELL_NAMESPACE
    )
    changeset_id = _seed_changeset(table_client, rows)
    runtime = _runtime_with(table_client)
    requested_changesets: list[str] = []

    original_get = table_client.get_records_by_changeset

    def spy_get_records(changeset: str) -> pa.Table:
        requested_changesets.append(changeset)
        return original_get(changeset)

    monkeypatch.setattr(table_client, "get_records_by_changeset", spy_get_records)

    captured_actions: list[dict[str, Any]] = []

    def fake_bulk(client: Any, actions: Any, **kwargs: Any) -> tuple[int, list[Any]]:
        captured_actions[:] = list(actions)
        return len(captured_actions), []

    monkeypatch.setattr(transformer.elasticsearch.helpers, "bulk", fake_bulk)

    request = AxiellAdapterTransformerEvent(
        changeset_ids=[changeset_id], job_id="job-abc"
    )
    result = transformer.execute_transform(request, runtime=runtime)
    assert result.changeset_ids == [changeset_id]
    assert result.indexed == 2
    assert result.errors == []
    assert result.job_id == "job-abc"
    assert requested_changesets == [changeset_id]
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
    monkeypatch: pytest.MonkeyPatch, temporary_table: IcebergTable
) -> None:
    table_client = IcebergTableClient(
        temporary_table, default_namespace=AXIELL_NAMESPACE
    )
    runtime = _runtime_with(table_client)

    def fake_bulk(*args: Any, **kwargs: Any) -> None:  # noqa: ARG001
        raise AssertionError("bulk should not be called when there are no documents")

    monkeypatch.setattr(transformer.elasticsearch.helpers, "bulk", fake_bulk)

    result = transformer.execute_transform(_request(), runtime=runtime)

    assert result.indexed == 0
    assert result.errors == []
    assert result.job_id == "job-abc"


def test_execute_transform_surfaces_errors(
    monkeypatch: pytest.MonkeyPatch, temporary_table: IcebergTable
) -> None:
    rows = [{"namespace": "axiell", "id": "ax-1", "content": "<xml />"}]
    table_client = IcebergTableClient(
        temporary_table, default_namespace=AXIELL_NAMESPACE
    )
    changeset_id = _seed_changeset(table_client, rows)
    runtime = _runtime_with(table_client)

    def fake_bulk(
        client: Any, actions: Any, **kwargs: Any
    ) -> tuple[int, list[dict[str, Any]]]:
        return 0, [{"index": {"_id": "ax-1", "status": 500}}]

    monkeypatch.setattr(transformer.elasticsearch.helpers, "bulk", fake_bulk)

    request = AxiellAdapterTransformerEvent(
        changeset_ids=[changeset_id], job_id="job-abc"
    )
    result = transformer.execute_transform(request, runtime=runtime)

    assert result.errors == ["id=ax-1 status=500"]
    assert result.indexed == 0
    assert result.job_id == "job-abc"


def test_execute_transform_reads_multiple_changesets(
    monkeypatch: pytest.MonkeyPatch, temporary_table: IcebergTable
) -> None:
    table_client = IcebergTableClient(
        temporary_table, default_namespace=AXIELL_NAMESPACE
    )
    cs1 = _seed_changeset(
        table_client,
        [
            {
                "namespace": "axiell",
                "id": "cs1-a",
                "content": "<xml />",
            }
        ],
    )
    cs2 = _seed_changeset(
        table_client,
        [
            {
                "namespace": "axiell",
                "id": "cs2-a",
                "content": "<xml />",
            }
        ],
    )
    runtime = _runtime_with(table_client)
    captured_actions: list[dict[str, Any]] = []

    def fake_bulk(client: Any, actions: Any, **kwargs: Any) -> tuple[int, list[Any]]:
        captured_actions[:] = list(actions)
        return len(captured_actions), []

    monkeypatch.setattr(transformer.elasticsearch.helpers, "bulk", fake_bulk)

    request = AxiellAdapterTransformerEvent(changeset_ids=[cs1, cs2], job_id="job-xyz")
    result = transformer.execute_transform(request, runtime=runtime)

    assert result.indexed == 2
    assert result.changeset_ids == [cs1, cs2]
    assert {action["_id"] for action in captured_actions} == {"cs1-a", "cs2-a"}
