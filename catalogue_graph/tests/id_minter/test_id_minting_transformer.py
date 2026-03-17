"""Tests for IdMintingTransformer."""

from __future__ import annotations

from collections.abc import Generator
from typing import Any, cast

import pytest
from elasticsearch import Elasticsearch

from core.source import BaseSource
from id_minter.id_minting_transformer import (
    IdMintingTransformer,
    _build_source_identifier_string,
)
from id_minter.models.identifier import SourceId
from tests.mocks import MockElasticsearchClient

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


class FakeResolver:
    """Deterministic IdResolver that returns pre-configured canonical IDs."""

    def __init__(self, ids: dict[SourceId, str] | None = None):
        self.ids = ids or {}
        self.mint_calls: list[list[tuple[SourceId, SourceId | None]]] = []

    def lookup_ids(self, source_ids: list[SourceId]) -> dict[SourceId, str]:
        return {k: v for k, v in self.ids.items() if k in source_ids}

    def mint_ids(
        self, requests: list[tuple[SourceId, SourceId | None]]
    ) -> dict[SourceId, str]:
        self.mint_calls.append(requests)
        return {req[0]: self.ids[req[0]] for req in requests if req[0] in self.ids}


def _make_source_identifier(
    ontology_type: str = "Work",
    identifier_type: str = "sierra-system-number",
    value: str = "b1000001",
) -> dict:
    return {
        "ontologyType": ontology_type,
        "identifierType": {"id": identifier_type},
        "value": value,
    }


def _make_work_doc(
    source_identifier: dict | None = None,
    items: list[dict] | None = None,
) -> dict:
    si = source_identifier or _make_source_identifier()
    doc: dict[str, Any] = {
        "state": {"sourceIdentifier": si},
        "data": {"title": "Test Work"},
    }
    if items:
        doc["items"] = items
    return doc


class _StubSource(BaseSource):
    def __init__(self, docs: list[dict]):
        self.docs = docs

    def stream_raw(self) -> Generator[dict]:
        yield from self.docs


# ---------------------------------------------------------------------------
# Tests: _build_source_identifier_string
# ---------------------------------------------------------------------------


class TestBuildSourceIdentifierString:
    def test_builds_composite_string(self) -> None:
        si = _make_source_identifier("Work", "sierra-system-number", "b1000001")
        assert (
            _build_source_identifier_string(si) == "Work[sierra-system-number/b1000001]"
        )

    def test_miro_identifier(self) -> None:
        si = _make_source_identifier("Work", "miro-image-number", "A0001234")
        assert _build_source_identifier_string(si) == "Work[miro-image-number/A0001234]"


# ---------------------------------------------------------------------------
# Tests: transform
# ---------------------------------------------------------------------------


class TestTransform:
    def test_transforms_single_document(self) -> None:
        si = _make_source_identifier()
        doc = _make_work_doc(si)
        resolver = FakeResolver(
            ids={("Work", "sierra-system-number", "b1000001"): "abcd1234"}
        )

        es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))
        transformer = IdMintingTransformer(
            es_client=es_client,
            source_index="works-source-dev",
            source_identifiers=["Work[sierra-system-number/b1000001]"],
            resolver=resolver,
        )

        results = list(transformer.transform([doc]))

        assert len(results) == 1
        row_id, embedded = results[0]
        assert row_id == "Work[sierra-system-number/b1000001]"
        assert embedded["state"]["canonicalId"] == "abcd1234"

    def test_transforms_document_with_nested_identifiers(self) -> None:
        root_si = _make_source_identifier("Work", "sierra-system-number", "b1000001")
        item_si = _make_source_identifier("Item", "sierra-system-number", "i2000001")
        item = {
            "sourceIdentifier": item_si,
            "type": "Identifiable",
            "identifiedType": "Identified",
        }
        doc = _make_work_doc(root_si, items=[item])
        doc["state"]["type"] = "Identifiable"
        doc["state"]["identifiedType"] = "Identified"

        resolver = FakeResolver(
            ids={
                ("Work", "sierra-system-number", "b1000001"): "abcd1234",
                ("Item", "sierra-system-number", "i2000001"): "efgh5678",
            }
        )

        es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))
        transformer = IdMintingTransformer(
            es_client=es_client,
            source_index="works-source-dev",
            source_identifiers=[],
            resolver=resolver,
        )

        results = list(transformer.transform([doc]))

        assert len(results) == 1
        _, embedded = results[0]
        assert embedded["state"]["canonicalId"] == "abcd1234"
        assert embedded["state"]["type"] == "Identified"
        assert "identifiedType" not in embedded["state"]
        assert embedded["items"][0]["canonicalId"] == "efgh5678"
        assert embedded["items"][0]["type"] == "Identified"
        assert "identifiedType" not in embedded["items"][0]

    def test_records_error_on_missing_state(self) -> None:
        doc: dict[str, Any] = {"data": {"title": "no state"}}

        es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))
        transformer = IdMintingTransformer(
            es_client=es_client,
            source_index="works-source-dev",
            source_identifiers=[],
            resolver=FakeResolver(),
        )

        results = list(transformer.transform([doc]))

        assert results == []
        assert len(transformer.errors) == 1
        assert transformer.errors[0].stage == "extract_id"

    def test_records_error_on_embedding_failure(self) -> None:
        si = _make_source_identifier()
        doc = _make_work_doc(si)

        class FailingResolver:
            def lookup_ids(self, source_ids: list[SourceId]) -> dict[SourceId, str]:
                return {}

            def mint_ids(
                self, requests: list[tuple[SourceId, SourceId | None]]
            ) -> dict[SourceId, str]:
                raise RuntimeError("DB connection failed")

        es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))
        transformer = IdMintingTransformer(
            es_client=es_client,
            source_index="works-source-dev",
            source_identifiers=[],
            resolver=FailingResolver(),
        )

        results = list(transformer.transform([doc]))

        assert results == []
        assert len(transformer.errors) == 1
        assert transformer.errors[0].stage == "embed"
        assert transformer.errors[0].row_id == "Work[sierra-system-number/b1000001]"


# ---------------------------------------------------------------------------
# Tests: _get_document_id
# ---------------------------------------------------------------------------


class TestGetDocumentId:
    def test_returns_canonical_id(self) -> None:
        es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))
        transformer = IdMintingTransformer(
            es_client=es_client,
            source_index="works-source-dev",
            source_identifiers=[],
            resolver=FakeResolver(),
        )

        record = {"state": {"canonicalId": "abcd1234", "sourceIdentifier": {}}}
        assert transformer._get_document_id(record) == "abcd1234"


# ---------------------------------------------------------------------------
# Tests: stream_to_index (integration)
# ---------------------------------------------------------------------------


class TestStreamToIndex:
    def test_indexes_transformed_documents(self) -> None:
        si = _make_source_identifier()
        doc = _make_work_doc(si)

        resolver = FakeResolver(
            ids={("Work", "sierra-system-number", "b1000001"): "abcd1234"}
        )

        MockElasticsearchClient.reset_mocks()
        es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))
        transformer = IdMintingTransformer(
            es_client=es_client,
            source_index="works-source-dev",
            source_identifiers=["Work[sierra-system-number/b1000001]"],
            resolver=resolver,
        )
        transformer.source = _StubSource([doc])

        transformer.stream_to_index(es_client, "works-identified-dev")

        assert len(MockElasticsearchClient.inputs) == 1
        indexed = MockElasticsearchClient.inputs[0]
        assert indexed["_index"] == "works-identified-dev"
        assert indexed["_id"] == "abcd1234"
        assert indexed["_source"]["state"]["canonicalId"] == "abcd1234"
        assert not transformer.errors
        assert transformer.successful_ids == ["abcd1234"]

    def test_tracks_indexing_errors(self, monkeypatch: pytest.MonkeyPatch) -> None:
        si = _make_source_identifier()
        doc = _make_work_doc(si)

        resolver = FakeResolver(
            ids={("Work", "sierra-system-number", "b1000001"): "abcd1234"}
        )

        def fake_bulk(
            client: Any,
            actions: Any,
            raise_on_error: bool = True,
            stats_only: bool = False,
        ) -> tuple[int, list]:
            actions_list = list(actions)
            return len(actions_list), [
                {
                    "index": {
                        "_id": "abcd1234",
                        "status": 400,
                        "error": {"type": "mapper_parsing_exception"},
                    }
                }
            ]

        monkeypatch.setattr("elasticsearch.helpers.bulk", fake_bulk)

        MockElasticsearchClient.reset_mocks()
        es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))
        transformer = IdMintingTransformer(
            es_client=es_client,
            source_index="works-source-dev",
            source_identifiers=[],
            resolver=resolver,
        )
        transformer.source = _StubSource([doc])

        transformer.stream_to_index(es_client, "works-identified-dev")

        assert len(transformer.errors) == 1
        assert transformer.errors[0].stage == "index"
        assert "mapper_parsing_exception" in transformer.errors[0].detail
