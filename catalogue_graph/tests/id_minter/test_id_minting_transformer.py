"""Tests for IdMintingTransformer."""

from __future__ import annotations

from collections.abc import Generator
from typing import Any, cast

import pytest
from elasticsearch import Elasticsearch

from id_minter.id_minting_source import IdMintingSource
from id_minter.id_minting_transformer import (
    IdMintingTransformer,
)
from id_minter.models.identifier import SourceIdentifierKey
from models.pipeline.identifier import SourceIdentifier
from tests.mocks import MockElasticsearchClient

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


class FakeResolver:
    """Deterministic IdResolver that returns pre-configured canonical IDs."""

    def __init__(self, ids: dict[SourceIdentifierKey, str] | None = None):
        self.ids = ids or {}
        self.mint_calls: list[
            list[tuple[SourceIdentifierKey, SourceIdentifierKey | None]]
        ] = []

    def lookup_ids(
        self, source_ids: list[SourceIdentifierKey]
    ) -> dict[SourceIdentifierKey, str]:
        return {k: v for k, v in self.ids.items() if k in source_ids}

    def mint_ids(
        self, requests: list[tuple[SourceIdentifierKey, SourceIdentifierKey | None]]
    ) -> dict[SourceIdentifierKey, str]:
        self.mint_calls.append(requests)
        # Mirror MintingResolver: enforce a 1:1 mapping between sourceIdentifier
        # and predecessorIdentifier within a single call.
        seen: dict[SourceIdentifierKey, SourceIdentifierKey | None] = {}
        for sid, pred in requests:
            if sid in seen and seen[sid] != pred:
                raise ValueError(
                    f"Conflicting predecessors for {sid[0]}/{sid[1]}/{sid[2]}"
                )
            seen[sid] = pred
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


class _StubSource(IdMintingSource):
    def __init__(self, docs: list[dict]):
        self.docs = docs

    def stream_raw(self) -> Generator[dict]:
        yield from self.docs


class TestBuildSourceIdentifierString:
    def test_builds_composite_string(self) -> None:
        si = SourceIdentifier.model_validate(
            _make_source_identifier("Work", "sierra-system-number", "b1000001")
        )
        assert str(si) == "Work[sierra-system-number/b1000001]"

    def test_miro_identifier(self) -> None:
        si = SourceIdentifier.model_validate(
            _make_source_identifier("Work", "miro-image-number", "A0001234")
        )
        assert str(si) == "Work[miro-image-number/A0001234]"


# ---------------------------------------------------------------------------
# Tests: transform
# ---------------------------------------------------------------------------


class TestTransform:
    def test_transforms_single_document(self) -> None:
        si = _make_source_identifier()
        doc = _make_work_doc(si)
        resolver = FakeResolver(
            ids={
                SourceIdentifierKey(
                    "Work", "sierra-system-number", "b1000001"
                ): "abcd1234"
            }
        )

        transformer = IdMintingTransformer(
            minting_source=_StubSource([doc]),
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
                SourceIdentifierKey(
                    "Work", "sierra-system-number", "b1000001"
                ): "abcd1234",
                SourceIdentifierKey(
                    "Item", "sierra-system-number", "i2000001"
                ): "efgh5678",
            }
        )

        transformer = IdMintingTransformer(
            minting_source=_StubSource([doc]),
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

        transformer = IdMintingTransformer(
            minting_source=_StubSource([doc]),
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
            def lookup_ids(
                self, source_ids: list[SourceIdentifierKey]
            ) -> dict[SourceIdentifierKey, str]:
                return {}

            def mint_ids(
                self,
                requests: list[tuple[SourceIdentifierKey, SourceIdentifierKey | None]],
            ) -> dict[SourceIdentifierKey, str]:
                raise RuntimeError("DB connection failed")

        transformer = IdMintingTransformer(
            minting_source=_StubSource([doc]),
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
        transformer = IdMintingTransformer(
            minting_source=_StubSource([]),
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
            ids={
                SourceIdentifierKey(
                    "Work", "sierra-system-number", "b1000001"
                ): "abcd1234"
            }
        )

        MockElasticsearchClient.reset_mocks()
        es_client = cast(Elasticsearch, MockElasticsearchClient({}, ""))
        transformer = IdMintingTransformer(
            minting_source=_StubSource([doc]),
            resolver=resolver,
        )

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
            ids={
                SourceIdentifierKey(
                    "Work", "sierra-system-number", "b1000001"
                ): "abcd1234"
            }
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
            minting_source=_StubSource([doc]),
            resolver=resolver,
        )

        transformer.stream_to_index(es_client, "works-identified-dev")

        assert len(transformer.errors) == 1
        assert transformer.errors[0].stage == "index"
        assert "mapper_parsing_exception" in transformer.errors[0].detail


# ---------------------------------------------------------------------------
# Tests: batched minting across multiple works
# ---------------------------------------------------------------------------


class TestBatchedMinting:
    def test_combines_requests_from_multiple_works_into_single_call(self) -> None:
        doc_a = _make_work_doc(
            _make_source_identifier("Work", "sierra-system-number", "b1000001")
        )
        doc_b = _make_work_doc(
            _make_source_identifier("Work", "sierra-system-number", "b1000002")
        )
        doc_c = _make_work_doc(
            _make_source_identifier("Work", "sierra-system-number", "b1000003")
        )

        resolver = FakeResolver(
            ids={
                SourceIdentifierKey(
                    "Work", "sierra-system-number", "b1000001"
                ): "aaaa1111",
                SourceIdentifierKey(
                    "Work", "sierra-system-number", "b1000002"
                ): "bbbb2222",
                SourceIdentifierKey(
                    "Work", "sierra-system-number", "b1000003"
                ): "cccc3333",
            }
        )

        transformer = IdMintingTransformer(
            minting_source=_StubSource([doc_a, doc_b, doc_c]),
            resolver=resolver,
        )

        results = list(transformer.transform([doc_a, doc_b, doc_c]))

        assert len(results) == 3
        # A single mint_ids call carrying all three works' source identifiers.
        assert len(resolver.mint_calls) == 1
        sids_in_call = {req[0] for req in resolver.mint_calls[0]}
        assert sids_in_call == {
            SourceIdentifierKey("Work", "sierra-system-number", "b1000001"),
            SourceIdentifierKey("Work", "sierra-system-number", "b1000002"),
            SourceIdentifierKey("Work", "sierra-system-number", "b1000003"),
        }
        canonical_ids = {row_id: doc["state"]["canonicalId"] for row_id, doc in results}
        assert canonical_ids == {
            "Work[sierra-system-number/b1000001]": "aaaa1111",
            "Work[sierra-system-number/b1000002]": "bbbb2222",
            "Work[sierra-system-number/b1000003]": "cccc3333",
        }

    def test_respects_mint_batch_size_chunking(self) -> None:
        docs = [
            _make_work_doc(
                _make_source_identifier("Work", "sierra-system-number", f"b100000{i}")
            )
            for i in range(5)
        ]
        resolver = FakeResolver(
            ids={
                SourceIdentifierKey(
                    "Work", "sierra-system-number", f"b100000{i}"
                ): f"canon{i:04d}"
                for i in range(5)
            }
        )

        transformer = IdMintingTransformer(
            minting_source=_StubSource(docs),
            resolver=resolver,
            mint_batch_size=2,
        )

        results = list(transformer.transform(docs))

        assert len(results) == 5
        # 5 works at batch size 2 -> 3 chunks (2 + 2 + 1).
        assert len(resolver.mint_calls) == 3
        assert [len(c) for c in resolver.mint_calls] == [2, 2, 1]

    def test_falls_back_to_per_work_when_batch_mint_fails(self) -> None:
        doc_good = _make_work_doc(
            _make_source_identifier("Work", "sierra-system-number", "b1000001")
        )
        doc_bad = _make_work_doc(
            _make_source_identifier("Work", "sierra-system-number", "b1000002")
        )
        doc_other = _make_work_doc(
            _make_source_identifier("Work", "sierra-system-number", "b1000003")
        )

        bad_sid: SourceIdentifierKey = SourceIdentifierKey(
            "Work", "sierra-system-number", "b1000002"
        )

        class FlakyResolver:
            """Fails when ``bad_sid`` is in the batch; otherwise resolves all."""

            def __init__(self) -> None:
                self.mint_calls: list[
                    list[tuple[SourceIdentifierKey, SourceIdentifierKey | None]]
                ] = []
                self.ids = {
                    SourceIdentifierKey(
                        "Work", "sierra-system-number", "b1000001"
                    ): "aaaa1111",
                    SourceIdentifierKey(
                        "Work", "sierra-system-number", "b1000003"
                    ): "cccc3333",
                }

            def lookup_ids(
                self, source_ids: list[SourceIdentifierKey]
            ) -> dict[SourceIdentifierKey, str]:
                return {k: v for k, v in self.ids.items() if k in source_ids}

            def mint_ids(
                self,
                requests: list[tuple[SourceIdentifierKey, SourceIdentifierKey | None]],
            ) -> dict[SourceIdentifierKey, str]:
                self.mint_calls.append(requests)
                if any(req[0] == bad_sid for req in requests):
                    raise RuntimeError("simulated batch failure")
                return {
                    req[0]: self.ids[req[0]] for req in requests if req[0] in self.ids
                }

        resolver = FlakyResolver()
        transformer = IdMintingTransformer(
            minting_source=_StubSource([doc_good, doc_bad, doc_other]),
            resolver=resolver,
        )

        results = list(transformer.transform([doc_good, doc_bad, doc_other]))

        # The two good works survive; the bad one is recorded as an embed error.
        row_ids = {row_id for row_id, _ in results}
        assert row_ids == {
            "Work[sierra-system-number/b1000001]",
            "Work[sierra-system-number/b1000003]",
        }
        assert len(transformer.errors) == 1
        err = transformer.errors[0]
        assert err.stage == "embed"
        assert err.row_id == "Work[sierra-system-number/b1000002]"

        # First call is the batched attempt; remaining calls are the per-work
        # fallback (one per work in the chunk).
        assert len(resolver.mint_calls) == 1 + 3

    def test_extract_id_failures_do_not_block_batch(self) -> None:
        good = _make_work_doc(
            _make_source_identifier("Work", "sierra-system-number", "b1000001")
        )
        broken: dict[str, Any] = {"data": {"title": "no state"}}

        resolver = FakeResolver(
            ids={
                SourceIdentifierKey(
                    "Work", "sierra-system-number", "b1000001"
                ): "aaaa1111"
            }
        )

        transformer = IdMintingTransformer(
            minting_source=_StubSource([good, broken]),
            resolver=resolver,
        )

        results = list(transformer.transform([good, broken]))

        assert len(results) == 1
        assert results[0][0] == "Work[sierra-system-number/b1000001]"
        assert len(transformer.errors) == 1
        assert transformer.errors[0].stage == "extract_id"
        # The broken doc shouldn't have made it into the resolver call.
        assert len(resolver.mint_calls) == 1
        sids_in_call = {req[0] for req in resolver.mint_calls[0]}
        assert sids_in_call == {
            SourceIdentifierKey("Work", "sierra-system-number", "b1000001")
        }

    def test_falls_back_to_per_work_on_conflicting_predecessors_in_chunk(
        self,
    ) -> None:
        # Two works that both reference the same shared item source id, but
        # disagree on its predecessorIdentifier. The combined request would
        # silently drop one predecessor inside the resolver, so the
        # transformer should detect the conflict and fall back to per-work
        # minting (one transaction per work) instead.
        shared_item_si = _make_source_identifier(
            "Item", "sierra-system-number", "i9000001"
        )
        pred_a = _make_source_identifier("Item", "sierra-system-number", "i9000000")
        pred_b = _make_source_identifier("Item", "sierra-system-number", "i9000099")

        item_with_pred_a = {
            "sourceIdentifier": shared_item_si,
            "predecessorIdentifier": pred_a,
            "type": "Identifiable",
            "identifiedType": "Identified",
        }
        item_with_pred_b = {
            "sourceIdentifier": shared_item_si,
            "predecessorIdentifier": pred_b,
            "type": "Identifiable",
            "identifiedType": "Identified",
        }

        doc_a = _make_work_doc(
            _make_source_identifier("Work", "sierra-system-number", "b1000001"),
            items=[item_with_pred_a],
        )
        doc_a["state"]["type"] = "Identifiable"
        doc_a["state"]["identifiedType"] = "Identified"
        doc_b = _make_work_doc(
            _make_source_identifier("Work", "sierra-system-number", "b1000002"),
            items=[item_with_pred_b],
        )
        doc_b["state"]["type"] = "Identifiable"
        doc_b["state"]["identifiedType"] = "Identified"

        resolver = FakeResolver(
            ids={
                SourceIdentifierKey(
                    "Work", "sierra-system-number", "b1000001"
                ): "aaaa1111",
                SourceIdentifierKey(
                    "Work", "sierra-system-number", "b1000002"
                ): "bbbb2222",
                SourceIdentifierKey(
                    "Item", "sierra-system-number", "i9000001"
                ): "iiii9999",
            }
        )

        transformer = IdMintingTransformer(
            minting_source=_StubSource([doc_a, doc_b]),
            resolver=resolver,
        )

        results = list(transformer.transform([doc_a, doc_b]))

        # Both works are still minted, but via the per-work fallback path.
        # The first call is the batched attempt that raises ValueError inside
        # the resolver (because the chunk references the same source id with
        # two different predecessors); the next two are the per-work
        # fallback, one mint_ids call per work.
        assert {row_id for row_id, _ in results} == {
            "Work[sierra-system-number/b1000001]",
            "Work[sierra-system-number/b1000002]",
        }
        assert len(resolver.mint_calls) == 1 + 2
        for call in resolver.mint_calls[1:]:
            work_keys = {req[0] for req in call if req[0][0] == "Work"}
            assert len(work_keys) == 1
