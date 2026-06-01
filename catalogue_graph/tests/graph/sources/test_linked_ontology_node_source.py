"""Unit tests for WikidataLinkedOntologyNodeSource._stream_filtered_wikidata_ids."""

import pytest

from graph.sources.wikidata.linked_ontology_node_source import (
    WikidataLinkedOntologyNodeSource,
)
from models.events import ExtractorEvent
from tests.graph.sources.test_linked_ontology_edge_source import (
    _patch_stream_and_validators,
)


@pytest.fixture
def concepts_source() -> WikidataLinkedOntologyNodeSource:
    return WikidataLinkedOntologyNodeSource(
        linked_transformer="loc_concepts",
        event=ExtractorEvent(
            pipeline_date="test",
            environment="prod",
            transformer_type="wikidata_linked_loc_concepts",
            entity_type="nodes",
        ),
    )


@pytest.fixture
def names_source() -> WikidataLinkedOntologyNodeSource:
    return WikidataLinkedOntologyNodeSource(
        linked_transformer="loc_names",
        event=ExtractorEvent(
            pipeline_date="test",
            environment="prod",
            transformer_type="wikidata_linked_loc_names",
            entity_type="nodes",
        ),
    )


def test_yields_ids_with_valid_linked_ids(
    concepts_source: WikidataLinkedOntologyNodeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Only Wikidata IDs whose linked_id passes both ontology and transformer checks are yielded."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={
            "same_as_loc": [
                {"from_id": "Q1", "to_id": "sh001"},  # valid concept
                {"from_id": "Q2", "to_id": "n001"},  # not valid for transformer
                {"from_id": "Q3", "to_id": "sh002"},  # valid concept
            ],
        },
        valid_for_transformer={"loc_concepts": {"sh001", "sh002"}},
        valid_for_ontology={"loc": {"sh001", "n001", "sh002"}},
    )

    result = list(concepts_source._stream_filtered_wikidata_ids())
    assert result == ["Q1", "Q3"]


def test_skips_ids_with_invalid_linked_ontology_id(
    concepts_source: WikidataLinkedOntologyNodeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """IDs whose linked_id is not valid for the ontology at all are skipped."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={
            "same_as_loc": [
                {"from_id": "Q1", "to_id": "invalid_id"},
            ],
        },
        valid_for_transformer={"loc_concepts": set()},
        valid_for_ontology={"loc": set()},  # nothing valid in ontology
    )

    assert list(concepts_source._stream_filtered_wikidata_ids()) == []


def test_no_ids_when_no_edges(
    concepts_source: WikidataLinkedOntologyNodeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Zero IDs emitted when there are no SAME_AS edges."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={},
        valid_for_transformer={},
        valid_for_ontology={},
    )

    assert list(concepts_source._stream_filtered_wikidata_ids()) == []


def test_deduplicates_wikidata_ids(
    concepts_source: WikidataLinkedOntologyNodeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """A Wikidata ID appearing in multiple edges is only yielded once."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={
            "same_as_loc": [
                {"from_id": "Q1", "to_id": "sh001"},
                {"from_id": "Q1", "to_id": "sh002"},  # duplicate Q1
            ],
        },
        valid_for_transformer={"loc_concepts": {"sh001", "sh002"}},
        valid_for_ontology={"loc": {"sh001", "sh002"}},
    )

    result = list(concepts_source._stream_filtered_wikidata_ids())
    assert result == ["Q1"]


def test_seen_but_not_yielded_ids_are_not_duplicated_as_parents(
    concepts_source: WikidataLinkedOntologyNodeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """IDs valid in the ontology but not for the transformer are 'seen' and won't reappear as parents."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={
            "same_as_loc": [
                {"from_id": "Q1", "to_id": "sh001"},  # valid concept
                {"from_id": "Q2", "to_id": "n001"},  # not valid for transformer
            ],
            "instance_of": [
                {"from_id": "Q1", "to_id": "Q2"},  # Q2 not yielded as parent
            ],
        },
        valid_for_transformer={"loc_concepts": {"sh001"}},
        valid_for_ontology={"loc": {"sh001", "n001"}},
    )

    result = list(concepts_source._stream_filtered_wikidata_ids())
    assert result == ["Q1"]
    assert "Q2" not in result


def test_concepts_yield_unseen_parents(
    concepts_source: WikidataLinkedOntologyNodeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Parent IDs not already seen via SAME_AS are yielded for concepts."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={
            "same_as_loc": [
                {"from_id": "Q1", "to_id": "sh001"},
            ],
            "instance_of": [
                {"from_id": "Q1", "to_id": "Q10"},  # new parent
            ],
            "subclass_of": [
                {"from_id": "Q1", "to_id": "Q11"},  # another new parent
            ],
        },
        valid_for_transformer={"loc_concepts": {"sh001"}},
        valid_for_ontology={"loc": {"sh001"}},
    )

    result = list(concepts_source._stream_filtered_wikidata_ids())
    assert result == ["Q1", "Q10", "Q11"]


def test_concepts_do_not_duplicate_parent_already_in_same_as(
    concepts_source: WikidataLinkedOntologyNodeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """A parent ID already seen during SAME_AS processing is not yielded again."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={
            "same_as_loc": [
                {"from_id": "Q1", "to_id": "sh001"},
                {"from_id": "Q2", "to_id": "sh002"},
            ],
            "instance_of": [
                {"from_id": "Q1", "to_id": "Q2"},  # Q2 already seen
            ],
        },
        valid_for_transformer={"loc_concepts": {"sh001", "sh002"}},
        valid_for_ontology={"loc": {"sh001", "sh002"}},
    )

    result = list(concepts_source._stream_filtered_wikidata_ids())
    assert result == ["Q1", "Q2"]


def test_parent_ids_are_deduplicated(
    concepts_source: WikidataLinkedOntologyNodeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """The same parent appearing in multiple edges is only yielded once."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={
            "same_as_loc": [
                {"from_id": "Q1", "to_id": "sh001"},
            ],
            "instance_of": [
                {"from_id": "Q1", "to_id": "Q10"},
                {"from_id": "Q2", "to_id": "Q10"},  # Q10 already seen as parent
            ],
        },
        valid_for_transformer={"loc_concepts": {"sh001"}},
        valid_for_ontology={"loc": {"sh001"}},
    )

    result = list(concepts_source._stream_filtered_wikidata_ids())
    assert result.count("Q10") == 1


def test_names_do_not_yield_parents(
    names_source: WikidataLinkedOntologyNodeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Names node source does not stream parent nodes."""
    _patch_stream_and_validators(
        names_source,
        monkeypatch,
        edges_by_type={
            "same_as_loc": [
                {"from_id": "Q100", "to_id": "n001"},
            ],
            "instance_of": [
                {"from_id": "Q100", "to_id": "Q200"},
            ],
        },
        valid_for_transformer={"loc_names": {"n001"}},
        valid_for_ontology={"loc": {"n001"}},
    )

    result = list(names_source._stream_filtered_wikidata_ids())
    assert result == ["Q100"]
