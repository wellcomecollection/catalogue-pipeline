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

Note: The Python embedder mirrors the Scala id_minter wire shape for
minted identifiers by rewriting identifiedType → type when a canonical ID is
embedded.
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
    TYPES_NORMALIZED_TO_CONCEPT,
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

        assert keys == [(key_of(si), None)]

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

        assert set(keys) == {(key_of(si), None) for si in sis}

    def test_retrieves_predecessor_alongside_source_identifier(self) -> None:
        si = create_source_identifier(identifier_type="axiell-system-number")
        pred = create_source_identifier(identifier_type="sierra-system-number")
        doc = {"sourceIdentifier": si, "predecessorIdentifier": pred}

        keys = extract_source_identifiers(doc)

        assert keys == [(key_of(si), key_of(pred))]

    def test_mixed_nodes_with_and_without_predecessors(self) -> None:
        si1 = create_source_identifier()
        si2 = create_source_identifier(identifier_type="axiell-system-number")
        pred2 = create_source_identifier(identifier_type="sierra-system-number")
        doc = {
            "sourceIdentifier": si1,
            "items": [
                {"sourceIdentifier": si2, "predecessorIdentifier": pred2},
            ],
        }

        keys = extract_source_identifiers(doc)

        assert set(keys) == {(key_of(si1), None), (key_of(si2), key_of(pred2))}

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

    def test_replaces_identified_type_with_type(self) -> None:
        """Minted nodes are promoted to the identified wire shape."""
        si1 = create_source_identifier()
        si2 = create_source_identifier()
        cid1 = create_canonical_id()
        cid2 = create_canonical_id()
        doc = {
            "sourceIdentifier": si1,
            "type": "Identifiable",
            "identifiedType": "NewType",
            "moreThings": [
                {
                    "sourceIdentifier": si2,
                    "type": "Identifiable",
                    "identifiedType": "AnotherNewType",
                },
            ],
        }

        id_map = {key_of(si1): cid1, key_of(si2): cid2}
        result = embed_canonical_ids(doc, id_map)

        assert "identifiedType" not in result
        assert result["type"] == "NewType"
        assert "identifiedType" not in result["moreThings"][0]
        assert result["moreThings"][0]["type"] == "AnotherNewType"
        assert result["canonicalId"] == cid1
        assert result["moreThings"][0]["canonicalId"] == cid2

    def test_promotes_identifiable_without_identified_type_field(self) -> None:
        si = create_source_identifier()
        cid = create_canonical_id()
        doc = {
            "sourceIdentifier": si,
            "type": "Identifiable",
        }

        result = embed_canonical_ids(doc, {key_of(si): cid})

        assert result["canonicalId"] == cid
        assert result["type"] == "Identified"

    def test_only_promotes_minted_nodes(self) -> None:
        si1 = create_source_identifier()
        si2 = create_source_identifier()
        cid1 = create_canonical_id()
        doc = {
            "sourceIdentifier": si1,
            "type": "Identifiable",
            "identifiedType": "Identified",
            "moreThings": [
                {
                    "sourceIdentifier": si2,
                    "type": "Identifiable",
                    "identifiedType": "Identified",
                }
            ],
        }

        result = embed_canonical_ids(doc, {key_of(si1): cid1})

        assert result["canonicalId"] == cid1
        assert result["type"] == "Identified"
        assert "identifiedType" not in result
        assert result["moreThings"][0]["type"] == "Identifiable"
        assert result["moreThings"][0]["identifiedType"] == "Identified"
        assert "canonicalId" not in result["moreThings"][0]

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
        for subtype in TYPES_NORMALIZED_TO_CONCEPT:
            assert normalize_ontology_type(subtype) == "Concept"

    def test_non_concept_types_unchanged(self) -> None:
        assert normalize_ontology_type("Work") == "Work"
        assert normalize_ontology_type("Image") == "Image"


class TestProcessWork:
    def test_single_identifier_minted(self) -> None:
        si = create_source_identifier()
        cid = create_canonical_id()
        doc = {"state": {"sourceIdentifier": si}, "sourceIdentifier": si}

        key = key_of(si)
        resolver = FakeResolver(ids={(key[0], key[1], key[2]): cid})

        result = process_work(doc, resolver)

        assert result["canonicalId"] == cid
        assert len(resolver.mint_calls) == 1

    def test_unknown_identifier_is_minted(self) -> None:
        """When the resolver doesn't already know an ID, mint_ids creates it."""
        si = create_source_identifier()
        cid = create_canonical_id()
        doc = {"state": {"sourceIdentifier": si}, "sourceIdentifier": si}

        key = key_of(si)
        resolver = FakeResolver(ids={(key[0], key[1], key[2]): cid})

        result = process_work(doc, resolver)

        assert result["canonicalId"] == cid
        assert len(resolver.mint_calls) == 1
        assert resolver.mint_calls[0][0] == ((key[0], key[1], key[2]), None)

    def test_multiple_identifiers_minted(self) -> None:
        si1 = create_source_identifier()
        si2 = create_source_identifier()
        cid1 = create_canonical_id()
        cid2 = create_canonical_id()
        doc = {
            "state": {"sourceIdentifier": si1},
            "sourceIdentifier": si1,
            "type": "Identifiable",
            "items": [{"sourceIdentifier": si2, "type": "Identifiable"}],
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
        assert result["type"] == "Identified"
        assert result["items"][0]["canonicalId"] == cid2
        assert result["items"][0]["type"] == "Identified"
        assert len(resolver.mint_calls) == 1

    def test_mix_of_existing_and_new_identifiers(self) -> None:
        """Resolver returns IDs for all — some looked up, some newly minted."""
        si1 = create_source_identifier()
        si2 = create_source_identifier()
        cid1 = create_canonical_id()
        cid2 = create_canonical_id()
        doc = {
            "state": {"sourceIdentifier": si1},
            "sourceIdentifier": si1,
            "items": [{"sourceIdentifier": si2}],
        }

        k1 = key_of(si1)
        k2 = key_of(si2)
        # Simulate a resolver that returns both (one existing, one new)
        resolver = FakeResolver(ids={})
        resolver.mint_ids = lambda reqs: (  # type: ignore[assignment]
            resolver.mint_calls.append(reqs)  # type: ignore[func-returns-value]
            or {
                (k1[0], k1[1], k1[2]): cid1,
                (k2[0], k2[1], k2[2]): cid2,
            }
        )

        result = process_work(doc, resolver)

        assert result["canonicalId"] == cid1
        assert result["items"][0]["canonicalId"] == cid2
        assert len(resolver.mint_calls) == 1

    def test_with_predecessor_inheritance(self) -> None:
        si_new = create_source_identifier(identifier_type="axiell-collections-id")
        si_pred = create_source_identifier(identifier_type="sierra-system-number")
        cid = create_canonical_id()
        doc = {
            "state": {"sourceIdentifier": si_new, "predecessorIdentifier": si_pred},
        }

        k_new = key_of(si_new)
        k_pred = key_of(si_pred)
        resolver = FakeResolver(ids={(k_new[0], k_new[1], k_new[2]): cid})

        result = process_work(doc, resolver)

        assert result["state"]["canonicalId"] == cid
        assert len(resolver.mint_calls) == 1
        assert (k_new, k_pred) in resolver.mint_calls[0]

    def test_no_data_identifiers_still_mints_state(self) -> None:
        """State always has a sourceIdentifier; it gets minted even if nothing else does."""
        si = create_source_identifier()
        cid = create_canonical_id()
        doc = {
            "state": {"sourceIdentifier": si},
            "title": "A work with no other sourceIdentifiers",
        }

        key = key_of(si)
        resolver = FakeResolver(ids={(key[0], key[1], key[2]): cid})

        result = process_work(doc, resolver)

        assert result["title"] == "A work with no other sourceIdentifiers"
        assert result["state"]["canonicalId"] == cid
        assert len(resolver.mint_calls) == 1

    def test_nested_item_with_predecessor(self) -> None:
        """A data entity's sourceIdentifier, eg. an item, can have its own predecessorIdentifier."""
        si_work = create_source_identifier()
        si_item = create_source_identifier(
            identifier_type="axiell-system-number", ontology_type="Item"
        )
        si_item_pred = create_source_identifier(
            identifier_type="sierra-system-number", ontology_type="Item"
        )
        cid_work = create_canonical_id()
        cid_item = create_canonical_id()
        doc = {
            "state": {"sourceIdentifier": si_work},
            "sourceIdentifier": si_work,
            "items": [
                {
                    "sourceIdentifier": si_item,
                    "predecessorIdentifier": si_item_pred,
                }
            ],
        }

        k_work = key_of(si_work)
        k_item = key_of(si_item)
        k_item_pred = key_of(si_item_pred)
        resolver = FakeResolver(
            ids={
                (k_work[0], k_work[1], k_work[2]): cid_work,
                (k_item[0], k_item[1], k_item[2]): cid_item,
            }
        )

        result = process_work(doc, resolver)

        assert result["canonicalId"] == cid_work
        assert result["items"][0]["canonicalId"] == cid_item
        assert (k_work, None) in resolver.mint_calls[0]
        assert (k_item, k_item_pred) in resolver.mint_calls[0]

    def test_state_with_predecessor(self) -> None:
        """State's sourceIdentifier can also have a predecessorIdentifier."""
        si_new = create_source_identifier(identifier_type="axiell-system-number")
        si_pred = create_source_identifier(identifier_type="sierra-system-number")
        cid = create_canonical_id()
        doc = {
            "state": {
                "sourceIdentifier": si_new,
                "predecessorIdentifier": si_pred,
            },
            "sourceIdentifier": si_new,
        }

        k_new = key_of(si_new)
        k_pred = key_of(si_pred)
        resolver = FakeResolver(ids={(k_new[0], k_new[1], k_new[2]): cid})

        result = process_work(doc, resolver)

        assert result["state"]["canonicalId"] == cid
        assert result["canonicalId"] == cid
        assert (k_new, k_pred) in resolver.mint_calls[0]

    def test_multiple_predecessors_across_doc(self) -> None:
        """Both state and a nested item have predecessors."""
        si_work = create_source_identifier(identifier_type="axiell-system-number")
        si_work_pred = create_source_identifier(identifier_type="sierra-system-number")
        si_item = create_source_identifier(
            identifier_type="axiell-system-number", ontology_type="Item"
        )
        si_item_pred = create_source_identifier(
            identifier_type="sierra-system-number", ontology_type="Item"
        )
        cid_work = create_canonical_id()
        cid_item = create_canonical_id()
        doc = {
            "state": {
                "sourceIdentifier": si_work,
                "predecessorIdentifier": si_work_pred,
            },
            "sourceIdentifier": si_work,
            "predecessorIdentifier": si_work_pred,
            "items": [
                {
                    "sourceIdentifier": si_item,
                    "predecessorIdentifier": si_item_pred,
                }
            ],
        }

        k_work = key_of(si_work)
        k_work_pred = key_of(si_work_pred)
        k_item = key_of(si_item)
        k_item_pred = key_of(si_item_pred)
        resolver = FakeResolver(
            ids={
                (k_work[0], k_work[1], k_work[2]): cid_work,
                (k_item[0], k_item[1], k_item[2]): cid_item,
            }
        )

        result = process_work(doc, resolver)

        assert result["canonicalId"] == cid_work
        assert result["items"][0]["canonicalId"] == cid_item
        assert (k_work, k_work_pred) in resolver.mint_calls[0]
        assert (k_item, k_item_pred) in resolver.mint_calls[0]
