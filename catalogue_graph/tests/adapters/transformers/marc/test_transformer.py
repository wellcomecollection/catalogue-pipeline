from __future__ import annotations

from collections.abc import Generator
from datetime import datetime
from typing import Any, cast

import pytest
import structlog
from elasticsearch import Elasticsearch
from structlog.testing import capture_logs

from adapters.utils.adapter_store import AdapterStore
from core.sinks import ElasticsearchSink
from core.source import BaseSource
from tests.adapters.transformers.marc.marcxml_test_transformer import (
    MarcXmlTransformerForTests,
)
from tests.mocks import ListSink, MockElasticsearchClient


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

    transformer.source = _StubSource(  # type: ignore[assignment]
        [{"id": "work1", "content": "", "last_modified": datetime.now()}]
    )
    sink = ListSink()
    result = transformer.stream_to(sink)
    works = sink.documents

    assert len(works) == 0
    assert len(result.errors) == 1
    assert result.errors[0].stage == "transform"
    assert "Missing content" in result.errors[0].detail


def test_transform_invalid_xml_records_error(adapter_store: AdapterStore) -> None:
    transformer = MarcXmlTransformerForTests(adapter_store, [])

    transformer.source = _StubSource(  # type: ignore[assignment]
        [
            {
                "id": "work2",
                "content": "<record><leader>bad",
                "last_modified": datetime.now(),
            }
        ]
    )
    sink = ListSink()
    result = transformer.stream_to(sink)
    works = sink.documents

    assert works == []
    assert result.errors
    assert result.errors[0].stage == "parse"
    assert result.errors[0].row_id == "work2"


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
    document = works[0]
    assert document.source_id == "marc12345"
    assert document.body["type"] == "Visible"
    assert document.body["data"]["title"] == "A Useful Title"
    # Null fields (e.g. predecessorIdentifier) must not reach the index
    assert "predecessorIdentifier" not in document.body["state"]


def test_extractor_logs_carry_the_row_id(adapter_store: AdapterStore) -> None:
    """Field extractors log without knowing the record; the transformer binds its id."""
    transformer = MarcXmlTransformerForTests(adapter_store, [])

    xml = (
        "<record>"
        "<leader>00000nam a2200000   4500</leader>"
        "<controlfield tag='001'>marc12345</controlfield>"
        "<datafield tag='245' ind1='0' ind2='0'>"
        "<subfield code='a'>First title</subfield>"
        "</datafield>"
        "<datafield tag='245' ind1='0' ind2='0'>"
        "<subfield code='a'>Second title</subfield>"
        "</datafield>"
        "</record>"
    )

    with capture_logs(processors=[structlog.contextvars.merge_contextvars]) as entries:
        list(
            transformer.transform(
                [{"id": "marc12345", "content": xml, "last_modified": datetime.now()}]
            )
        )

    errors = [e for e in entries if e["log_level"] == "error"]
    assert errors, entries
    assert all(e["row_id"] == "marc12345" for e in errors)
    assert structlog.contextvars.get_contextvars() == {}


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

    transformer.source = _StubSource(  # type: ignore[assignment]
        [{"id": "marcErr123", "content": xml, "last_modified": datetime.now()}]
    )
    sink = ListSink()
    result = transformer.stream_to(sink)
    works = sink.documents

    assert works == []
    assert result.errors
    assert result.errors[0].stage == "transform"
    assert "boom: bad data" in result.errors[0].detail


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

    transformer.source = _StubSource(  # type: ignore[assignment]
        [
            {
                "id": "work3",
                "content": MISSING_001_XML,
                "last_modified": datetime.now(),
            }
        ]
    )
    sink = ListSink()
    result = transformer.stream_to(sink)
    works = sink.documents

    assert works == []
    assert result.errors == []


def test_transform_skips_record_with_empty_001(adapter_store: AdapterStore) -> None:
    transformer = MarcXmlTransformerForTests(adapter_store, [])

    transformer.source = _StubSource(  # type: ignore[assignment]
        [{"id": "work4", "content": EMPTY_001_XML, "last_modified": datetime.now()}]
    )
    sink = ListSink()
    result = transformer.stream_to(sink)
    works = sink.documents

    assert works == []
    assert result.errors == []


def test_transform_skips_deleted_record_without_001(
    adapter_store: AdapterStore,
) -> None:
    """An id-less deleted row must not emit a tombstone either."""
    transformer = MarcXmlTransformerForTests(adapter_store, [])

    transformer.source = _StubSource(  # type: ignore[assignment]
        [
            {
                "id": "work5",
                "content": MISSING_001_XML,
                "last_modified": datetime.now(),
                "deleted": True,
            }
        ]
    )
    sink = ListSink()
    result = transformer.stream_to(sink)
    works = sink.documents

    assert works == []
    assert result.errors == []


def test_stream_to_skips_id_less_records_and_warns_per_record(
    adapter_store: AdapterStore,
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

    MockElasticsearchClient.inputs.clear()
    es_client = MockElasticsearchClient({}, "")
    with capture_logs() as logs:
        result = transformer.stream_to(
            ElasticsearchSink(cast(Elasticsearch, es_client), "works-source-dev")
        )

    # The valid record is indexed as before.
    assert {a["_id"] for a in MockElasticsearchClient.inputs} == {"Work[marc-test/id1]"}

    # Other failure classes still count as failures.
    assert len(result.errors) == 1
    assert result.errors[0].row_id == "idbad"
    assert "Missing title field (245)" in result.errors[0].detail

    # Id-less records are skipped with a warning naming each row.
    warnings = [
        log
        for log in logs
        if log["event"] == "Skipping record with a missing or empty id field (001)"
    ]
    assert all(log["log_level"] == "warning" for log in warnings)
    assert {log["row_id"] for log in warnings} == {"id2", "id3"}


def test_stream_to_no_skip_warning_when_all_records_have_ids(
    adapter_store: AdapterStore,
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

    MockElasticsearchClient.inputs.clear()
    es_client = MockElasticsearchClient({}, "")
    with capture_logs() as logs:
        transformer.stream_to(
            ElasticsearchSink(cast(Elasticsearch, es_client), "works-source-dev")
        )

    assert not [log for log in logs if "Skipping record" in log["event"]]


def test_stream_to_success_no_errors(
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
    result = transformer.stream_to(
        ElasticsearchSink(cast(Elasticsearch, es_client), "works-source-dev")
    )

    assert {a["_id"] for a in MockElasticsearchClient.inputs} == {
        "Work[marc-test/id1]",
        "Work[marc-test/id2]",
    }
    assert {a["_source"]["data"]["title"] for a in MockElasticsearchClient.inputs} == {
        "Title 1",
        "Title 2",
    }
    assert not result.errors


def test_stream_to_with_errors(
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
    result = transformer.stream_to(
        ElasticsearchSink(cast(Elasticsearch, es_client), "works-source-dev")
    )

    assert result.errors
    assert result.errors[0].stage == "index"
    assert result.errors[0].row_id == "id1"
    assert "mapper_parsing_exception" in result.errors[0].detail
