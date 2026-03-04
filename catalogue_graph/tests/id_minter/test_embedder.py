"""Tests for id_minter.embedder — Python equivalent of SourceIdentifierEmbedderTest.scala.

Covers the same test cases as the Scala tests:
  scan:
    - retrieves a sourceIdentifier at the root of the json
    - retrieves multiple sourceIdentifiers nested in the json
    - raises on invalid sourceIdentifier structure
  update (embed_canonical_ids):
    - adds a single canonicalId at the root
    - adds multiple nested canonicalIds
    - leaves nodes unchanged when id_map has no matching entry
  process_work (integration with IdResolver):
    - all IDs resolved via mint_ids
    - passes no predecessor by default
    - multiple identifiers resolved
    - with predecessor inheritance
    - empty work (no sourceIdentifiers)

Note: The Scala embedder also renames identifiedType → type. Our Python
embedder intentionally does NOT do this — it was classified as a Scala
serialization artefact in the verification notebook.
"""

from __future__ import annotations

import random
import string

import pytest

from id_minter.embedder import (
    embed_canonical_ids,
    extract_source_identifiers,
    make_key,
    normalize_ontology_type,
    process_work,
    scan,
)
from id_minter.models.identifier import (
    CONCEPT_SUBTYPES,
    SourceId,
    SourceIdentifierKey,
)

IDENTIFIER_TYPES = [
    "miro-image-number",
    "sierra-system-number",
    "calm-record-id",
]


def _random_alnum(length: int) -> str:
    return "".join(random.choices(string.ascii_lowercase + string.digits, k=length))


def create_source_identifier(
    identifier_type: str | None = None,
    value: str | None = None,
    ontology_type: str = "Work",
) -> dict:
    return {
        "identifierType": {"id": identifier_type or random.choice(IDENTIFIER_TYPES)},
        "value": value or _random_alnum(10),
        "ontologyType": ontology_type,
    }


def create_canonical_id() -> str:
    return _random_alnum(8)


def key_of(si: dict) -> SourceIdentifierKey:
    return make_key(si)


class FakeResolver:
    def __init__(self, ids: dict[SourceId, str] | None = None):
        self.ids = ids or {}
        self.lookup_calls: list[list[SourceId]] = []
        self.mint_calls: list[list[tuple[SourceId, SourceId | None]]] = []

    def lookup_ids(self, source_ids: list[SourceId]) -> dict[SourceId, str]:
        self.lookup_calls.append(source_ids)
        return {k: v for k, v in self.ids.items() if k in source_ids}

    def mint_ids(
        self, requests: list[tuple[SourceId, SourceId | None]]
    ) -> dict[SourceId, str]:
        self.mint_calls.append(requests)
        return {req[0]: self.ids[req[0]] for req in requests}


class TestScan:
    def test_retrieves_source_identifier_at_root(self) -> None:
        si = create_source_identifier()
        doc = {"sourceIdentifier": si}

        keys = extract_source_identifiers(doc)

        assert keys == [key_of(si)]

    def test_retrieves_multiple_nested_source_identifiers(self) -> None:
        sis = [create_source_identifier() for _ in range(4)]
        doc = {
            "sourceIdentifier": sis[0],
            "moreThings": [
                {"sourceIdentifier": sis[1]},
                {
                    "sourceIdentifier": sis[2],
                    "evenMoreThings": [
                        {"sourceIdentifier": sis[3]},
                    ],
                },
            ],
        }

        keys = extract_source_identifiers(doc)

        assert set(keys) == {key_of(si) for si in sis}

    def test_raises_on_invalid_source_identifier(self) -> None:
        doc = {"sourceIdentifier": {"something": "something"}}

        with pytest.raises(KeyError):
            extract_source_identifiers(doc)

    def test_scan_yields_matching_dicts(self) -> None:
        si = create_source_identifier()
        doc = {"sourceIdentifier": si, "other": "data"}

        nodes = list(scan(doc, lambda d: "sourceIdentifier" in d))

        assert len(nodes) == 1
        assert nodes[0]["sourceIdentifier"] == si


class TestEmbedCanonicalIds:
    def test_adds_single_canonical_id_at_root(self) -> None:
        si = create_source_identifier()
        cid = create_canonical_id()
        doc = {"sourceIdentifier": si}

        result = embed_canonical_ids(doc, {key_of(si): cid})

        assert result["canonicalId"] == cid
        assert result["sourceIdentifier"] == si

    def test_adds_multiple_nested_canonical_ids(self) -> None:
        sis = [create_source_identifier() for _ in range(4)]
        cids = [create_canonical_id() for _ in range(4)]
        doc = {
            "sourceIdentifier": sis[0],
            "moreThings": [
                {"sourceIdentifier": sis[1]},
                {
                    "sourceIdentifier": sis[2],
                    "evenMoreThings": [
                        {"sourceIdentifier": sis[3]},
                    ],
                },
            ],
        }

        id_map = {key_of(si): cid for si, cid in zip(sis, cids, strict=False)}
        result = embed_canonical_ids(doc, id_map)

        assert result["canonicalId"] == cids[0]
        assert result["moreThings"][0]["canonicalId"] == cids[1]
        assert result["moreThings"][1]["canonicalId"] == cids[2]
        assert result["moreThings"][1]["evenMoreThings"][0]["canonicalId"] == cids[3]

    def test_overwrites_existing_null_canonical_id(self) -> None:
        """Regression: source docs can have canonicalId: None pre-existing."""
        si = create_source_identifier()
        cid = create_canonical_id()
        doc = {"sourceIdentifier": si, "canonicalId": None}

        result = embed_canonical_ids(doc, {key_of(si): cid})

        assert result["canonicalId"] == cid

    def test_preserves_identified_type(self) -> None:
        """Scala renames identifiedType → type; we intentionally do not.

        Corresponds to Scala it("replaces identifiedType with type").
        """
        si1 = create_source_identifier()
        si2 = create_source_identifier()
        cid1 = create_canonical_id()
        cid2 = create_canonical_id()
        doc = {
            "sourceIdentifier": si1,
            "identifiedType": "NewType",
            "moreThings": [
                {
                    "sourceIdentifier": si2,
                    "identifiedType": "AnotherNewType",
                },
            ],
        }

        id_map = {key_of(si1): cid1, key_of(si2): cid2}
        result = embed_canonical_ids(doc, id_map)

        assert result["identifiedType"] == "NewType"
        assert "type" not in result
        assert result["moreThings"][0]["identifiedType"] == "AnotherNewType"
        assert "type" not in result["moreThings"][0]
        assert result["canonicalId"] == cid1
        assert result["moreThings"][0]["canonicalId"] == cid2

    def test_leaves_all_nodes_unchanged_with_empty_id_map(self) -> None:
        """With an empty id_map, no canonicalId is added to any node.

        Corresponds to Scala it("fails if it cannot match the identifier
        to any sourceIdentifier in the json") — but our embedder leaves
        nodes unchanged rather than failing.
        """
        si = create_source_identifier()
        doc = {"sourceIdentifier": si}

        result = embed_canonical_ids(doc, {})

        assert "canonicalId" not in result
        assert result["sourceIdentifier"] == si


class TestNormalizeOntologyType:
    def test_concept_subtypes_normalize_to_concept(self) -> None:
        for subtype in CONCEPT_SUBTYPES:
            assert normalize_ontology_type(subtype) == "Concept"

    def test_non_concept_types_unchanged(self) -> None:
        assert normalize_ontology_type("Work") == "Work"
        assert normalize_ontology_type("Image") == "Image"


class TestProcessWork:
    def test_all_ids_resolved(self) -> None:
        si = create_source_identifier()
        cid = create_canonical_id()
        doc = {"sourceIdentifier": si}

        key = key_of(si)
        resolver = FakeResolver(ids={(key[0], key[1], key[2]): cid})

        result = process_work(doc, resolver)

        assert result["canonicalId"] == cid
        assert len(resolver.mint_calls) == 1

    def test_passes_no_predecessor_by_default(self) -> None:
        si = create_source_identifier()
        cid = create_canonical_id()
        doc = {"sourceIdentifier": si}

        key = key_of(si)
        resolver = FakeResolver(ids={(key[0], key[1], key[2]): cid})

        result = process_work(doc, resolver)

        assert result["canonicalId"] == cid
        assert len(resolver.mint_calls) == 1
        assert resolver.mint_calls[0][0] == ((key[0], key[1], key[2]), None)

    def test_multiple_identifiers_resolved(self) -> None:
        si1 = create_source_identifier()
        si2 = create_source_identifier()
        cid1 = create_canonical_id()
        cid2 = create_canonical_id()
        doc = {
            "sourceIdentifier": si1,
            "items": [{"sourceIdentifier": si2}],
        }

        k1 = key_of(si1)
        k2 = key_of(si2)
        resolver = FakeResolver(
            ids={
                (k1[0], k1[1], k1[2]): cid1,
                (k2[0], k2[1], k2[2]): cid2,
            },
        )

        result = process_work(doc, resolver)

        assert result["canonicalId"] == cid1
        assert result["items"][0]["canonicalId"] == cid2

    def test_with_predecessor_inheritance(self) -> None:
        si_new = create_source_identifier(identifier_type="axiell-collections-id")
        si_pred = create_source_identifier(identifier_type="sierra-system-number")
        cid = create_canonical_id()
        doc = {"sourceIdentifier": si_new}

        k_new = key_of(si_new)
        k_pred = key_of(si_pred)
        resolver = FakeResolver(ids={(k_new[0], k_new[1], k_new[2]): cid})

        result = process_work(doc, resolver, predecessors={k_new: k_pred})

        assert result["canonicalId"] == cid
        assert len(resolver.mint_calls) == 1
        assert resolver.mint_calls[0][0][1] == (k_pred[0], k_pred[1], k_pred[2])

    def test_empty_work_returns_unchanged(self) -> None:
        doc = {"title": "A work with no identifiers"}
        resolver = FakeResolver()

        result = process_work(doc, resolver)

        assert result == doc
        assert not resolver.lookup_calls
        assert not resolver.mint_calls
