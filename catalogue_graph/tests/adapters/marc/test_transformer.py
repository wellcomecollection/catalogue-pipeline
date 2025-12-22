from __future__ import annotations

from collections.abc import Generator
from datetime import datetime
from typing import Any, cast

import pytest
from elasticsearch import Elasticsearch

from adapters.transformers.base_transformer import BaseSource
from adapters.utils.adapter_store import AdapterStore
from models.pipeline.source.work import DeletedSourceWork, VisibleSourceWork
from tests.mocks import MockElasticsearchClient

from .marcxml_test_transformer import MarcXmlTransformerWithStoreForTests


class _StubSource(BaseSource):
    def __init__(self, rows: list[dict[str, Any]]):
        self.rows = rows

    def stream_raw(self) -> Generator[dict[str, Any]]:
        yield from self.rows


def test_transform_empty_content_returns_deleted(adapter_store: AdapterStore) -> None:
    transformer = MarcXmlTransformerWithStoreForTests(adapter_store, [])

    works = list(
        transformer.transform(
            [{"id": "work1", "content": "", "last_modified": datetime.now()}]
        )
    )

    assert len(works) == 1
    assert isinstance(works[0], DeletedSourceWork)
    assert works[0].deleted_reason.type == "DeletedFromSource"
    assert not transformer.errors


def test_transform_invalid_xml_records_error(adapter_store: AdapterStore) -> None:
    transformer = MarcXmlTransformerWithStoreForTests(adapter_store, [])

    works = list(
        transformer.transform(
            [
                {
                    "id": "work2",
                    "content": "<record><leader>bad",
                    "last_modified": datetime.now(),
                }
            ]
        )
    )

    assert works == []
    assert transformer.errors
    assert transformer.errors[0].stage == "parse"
    assert transformer.errors[0].work_id == "work2"


def test_transform_valid_marcxml_returns_work(adapter_store: AdapterStore) -> None:
    transformer = MarcXmlTransformerWithStoreForTests(adapter_store, [])

    xml = (
        "<record>"
        "<leader>00000nam a2200000   4500</leader>"
        "<controlfield tag='001'>marc12345</controlfield>"
        "<datafield tag='245' ind1='0' ind2='0'>"
        "<subfield code='a'>A Useful Title</subfield>"
        "</datafield>"
        "</record>"
    )

    works = list(
        transformer.transform(
            [{"id": "marc12345", "content": xml, "last_modified": datetime.now()}]
        )
    )

    assert len(works) == 1
    assert isinstance(works[0], VisibleSourceWork)
    assert works[0].data.title == "A Useful Title"


def test_transform_handles_transform_record_exception(
    adapter_store: AdapterStore, monkeypatch: pytest.MonkeyPatch
) -> None:
    transformer = MarcXmlTransformerWithStoreForTests(adapter_store, [])

    def raising_transform_record(*_args: Any, **_kwargs: Any) -> Any:
        raise ValueError("boom: bad data")

    monkeypatch.setattr(transformer, "transform_record", raising_transform_record)

    xml = (
        "<record>"
        "<leader>00000nam a2200000   4500</leader>"
        "<controlfield tag='001'>marcErr123</controlfield>"
        "<datafield tag='245' ind1='0' ind2='0'>"
        "<subfield code='a'>Will Fail</subfield>"
        "</datafield>"
        "</record>"
    )

    works = list(
        transformer.transform(
            [{"id": "marcErr123", "content": xml, "last_modified": datetime.now()}]
        )
    )

    assert works == []
    assert transformer.errors
    assert transformer.errors[0].stage == "transform"
    assert "boom: bad data" in transformer.errors[0].detail


def test_stream_to_index_success_no_errors(
    adapter_store: AdapterStore,
) -> None:
    transformer = MarcXmlTransformerWithStoreForTests(adapter_store, [])
    transformer.source = _StubSource(
        [
            {
                "id": "id1",
                "content": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>id1</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Title 1</subfield></datafield></record>",
                "last_modified": datetime.now(),
            },
            {
                "id": "id2",
                "content": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>id2</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Title 2</subfield></datafield></record>",
                "last_modified": datetime.now(),
            },
        ]
    )

    MockElasticsearchClient.inputs.clear()
    es_client = MockElasticsearchClient({}, "")
    transformer.stream_to_index(cast(Elasticsearch, es_client), "works-source-dev")

    assert {a["_id"] for a in MockElasticsearchClient.inputs} == {
        "Work[marc-test/id1]",
        "Work[marc-test/id2]",
    }
    assert {a["_source"]["data"]["title"] for a in MockElasticsearchClient.inputs} == {
        "Title 1",
        "Title 2",
    }
    assert not transformer.errors


def test_stream_to_index_with_errors(
    adapter_store: AdapterStore, monkeypatch: pytest.MonkeyPatch
) -> None:
    transformer = MarcXmlTransformerWithStoreForTests(adapter_store, [])

    def fake_bulk(client, actions, raise_on_error, stats_only):  # type: ignore[no-untyped-def]
        actions_list = list(actions)
        return len(actions_list), [
            {
                "index": {
                    "_id": actions_list[0]["_id"],
                    "status": 400,
                    "error": {"type": "mapper_parsing_exception"},
                }
            }
        ]

    monkeypatch.setattr("elasticsearch.helpers.bulk", fake_bulk)

    transformer.source = _StubSource(
        [
            {
                "id": "id1",
                "content": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>id1</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Bad Title</subfield></datafield></record>",
                "last_modified": datetime.now(),
            }
        ]
    )

    MockElasticsearchClient.inputs.clear()
    es_client = MockElasticsearchClient({}, "")
    transformer.stream_to_index(cast(Elasticsearch, es_client), "works-source-dev")

    assert transformer.errors
    assert transformer.errors[0].stage == "index"
    assert transformer.errors[0].work_id == "Work[marc-test/id1]"
    assert "mapper_parsing_exception" in transformer.errors[0].detail
