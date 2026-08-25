from __future__ import annotations

from collections.abc import Generator
from datetime import datetime
from typing import Any, cast

import pytest
import structlog.testing
from elasticsearch import Elasticsearch

from adapters.utils.adapter_store import AdapterStore
from core.source import BaseSource
from models.pipeline.source.work import VisibleSourceWork
from tests.adapters.transformers.marc.marcxml_test_transformer import (
    MarcXmlTransformerForTests,
)
from tests.mocks import MockElasticsearchClient


@pytest.fixture
def adapter_store(temporary_table) -> AdapterStore:  # type: ignore[no-untyped-def]
    """Create an AdapterStore backed by a temporary local Iceberg table."""

    return AdapterStore(temporary_table, "test_namespace")


class _StubSource(BaseSource):
    def __init__(self, rows: list[dict[str, Any]]):
        self.rows = rows

    def stream_raw(self) -> Generator[dict[str, Any]]:
        yield from self.rows


def test_transform_missing_content_logs_error(adapter_store: AdapterStore) -> None:
    """Records without content should log an error and be skipped."""
    transformer = MarcXmlTransformerForTests(adapter_store, [])

    works = list(
        transformer.transform(
            [{"id": "work1", "content": "", "last_modified": datetime.now()}]
        )
    )

    assert len(works) == 0
    assert len(transformer.errors) == 1
    assert transformer.errors[0].stage == "transform"
    assert "Missing content" in transformer.errors[0].detail


def test_transform_invalid_xml_records_error(adapter_store: AdapterStore) -> None:
    transformer = MarcXmlTransformerForTests(adapter_store, [])

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
    assert transformer.errors[0].row_id == "work2"


def test_transform_valid_marcxml_returns_work(adapter_store: AdapterStore) -> None:
    transformer = MarcXmlTransformerForTests(adapter_store, [])

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
    row_id, work = works[0]
    assert row_id == "marc12345"
    assert isinstance(work, VisibleSourceWork)
    assert work.data.title == "A Useful Title"


def test_transform_handles_transform_record_exception(
    adapter_store: AdapterStore, monkeypatch: pytest.MonkeyPatch
) -> None:
    transformer = MarcXmlTransformerForTests(adapter_store, [])

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


MISSING_001_XML = (
    "<record>"
    "<leader>00000nam a2200000   4500</leader>"
    "<datafield tag='245' ind1='0' ind2='0'>"
    "<subfield code='a'>No Id At All</subfield>"
    "</datafield>"
    "</record>"
)

EMPTY_001_XML = (
    "<record>"
    "<leader>00000nam a2200000   4500</leader>"
    "<controlfield tag='001'></controlfield>"
    "<datafield tag='245' ind1='0' ind2='0'>"
    "<subfield code='a'>Empty Id</subfield>"
    "</datafield>"
    "</record>"
)


def test_transform_skips_record_with_missing_001(adapter_store: AdapterStore) -> None:
    transformer = MarcXmlTransformerForTests(adapter_store, [])

    works = list(
        transformer.transform(
            [
                {
                    "id": "work3",
                    "content": MISSING_001_XML,
                    "last_modified": datetime.now(),
                }
            ]
        )
    )

    assert works == []
    assert transformer.errors == []


def test_transform_skips_record_with_empty_001(adapter_store: AdapterStore) -> None:
    transformer = MarcXmlTransformerForTests(adapter_store, [])

    works = list(
        transformer.transform(
            [{"id": "work4", "content": EMPTY_001_XML, "last_modified": datetime.now()}]
        )
    )

    assert works == []
    assert transformer.errors == []


def test_transform_skips_deleted_record_without_001(
    adapter_store: AdapterStore,
) -> None:
    """An id-less deleted row must not emit a tombstone either."""
    transformer = MarcXmlTransformerForTests(adapter_store, [])

    works = list(
        transformer.transform(
            [
                {
                    "id": "work5",
                    "content": MISSING_001_XML,
                    "last_modified": datetime.now(),
                    "deleted": True,
                }
            ]
        )
    )

    assert works == []
    assert transformer.errors == []


def test_stream_to_index_skips_id_less_records_and_warns_per_record(
    adapter_store: AdapterStore, monkeypatch: pytest.MonkeyPatch
) -> None:
    missing_title_xml = (
        "<record>"
        "<leader>00000nam a2200000   4500</leader>"
        "<controlfield tag='001'>idbad</controlfield>"
        "</record>"
    )
    transformer = MarcXmlTransformerForTests(adapter_store, [])
    transformer.source = _StubSource(  # type: ignore[assignment]
        [
            {
                "id": "id1",
                "content": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>id1</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Title 1</subfield></datafield></record>",
                "last_modified": datetime.now(),
            },
            {"id": "id2", "content": MISSING_001_XML, "last_modified": datetime.now()},
            {"id": "id3", "content": EMPTY_001_XML, "last_modified": datetime.now()},
            {
                "id": "idbad",
                "content": missing_title_xml,
                "last_modified": datetime.now(),
            },
        ]
    )

    # Cached structlog config makes capture_logs unreliable; patch the module logger.
    logger = structlog.testing.CapturingLogger()
    monkeypatch.setattr("adapters.transformers.marcxml_transformer.logger", logger)

    MockElasticsearchClient.inputs.clear()
    es_client = MockElasticsearchClient({}, "")
    transformer.stream_to_index(cast(Elasticsearch, es_client), "works-source-dev")

    # The valid record is indexed as before.
    assert {a["_id"] for a in MockElasticsearchClient.inputs} == {"Work[marc-test/id1]"}

    # Other failure classes still count as failures.
    assert len(transformer.errors) == 1
    assert transformer.errors[0].row_id == "idbad"
    assert "Missing title field (245)" in transformer.errors[0].detail

    # Id-less records are skipped with a warning naming each row.
    warnings = [
        call
        for call in logger.calls
        if call.args
        and call.args[0] == "Skipping record with a missing or empty id field (001)"
    ]
    assert all(call.method_name == "warning" for call in warnings)
    assert {call.kwargs["row_id"] for call in warnings} == {"id2", "id3"}


def test_stream_to_index_no_skip_warning_when_all_records_have_ids(
    adapter_store: AdapterStore, monkeypatch: pytest.MonkeyPatch
) -> None:
    transformer = MarcXmlTransformerForTests(adapter_store, [])
    transformer.source = _StubSource(  # type: ignore[assignment]
        [
            {
                "id": "id1",
                "content": "<record><leader>00000nam a2200000   4500</leader><controlfield tag='001'>id1</controlfield><datafield tag='245' ind1='0' ind2='0'><subfield code='a'>Title 1</subfield></datafield></record>",
                "last_modified": datetime.now(),
            }
        ]
    )

    logger = structlog.testing.CapturingLogger()
    monkeypatch.setattr("adapters.transformers.marcxml_transformer.logger", logger)

    MockElasticsearchClient.inputs.clear()
    es_client = MockElasticsearchClient({}, "")
    transformer.stream_to_index(cast(Elasticsearch, es_client), "works-source-dev")

    assert not [
        call for call in logger.calls if call.args and "Skipping record" in call.args[0]
    ]


def test_stream_to_index_success_no_errors(
    adapter_store: AdapterStore,
) -> None:
    transformer = MarcXmlTransformerForTests(adapter_store, [])
    transformer.source = _StubSource(  # type: ignore[assignment]
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
    transformer = MarcXmlTransformerForTests(adapter_store, [])

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

    transformer.source = _StubSource(  # type: ignore[assignment]
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
    assert transformer.errors[0].row_id == "id1"
    assert "mapper_parsing_exception" in transformer.errors[0].detail
