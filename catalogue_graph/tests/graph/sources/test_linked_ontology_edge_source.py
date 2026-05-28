"""Unit tests for WikidataLinkedOntologyEdgeSource filtering logic."""

from collections.abc import Generator

import pytest

from graph.sources.wikidata.linked_ontology_edge_source import (
    WikidataLinkedOntologyEdgeSource,
)
from graph.sources.wikidata.linked_ontology_source import WikidataLinkedOntologySource
from graph.sources.wikidata.sparql_query_builder import WikidataEdgeQueryType
from models.events import ExtractorEvent


@pytest.fixture
def concepts_source() -> WikidataLinkedOntologyEdgeSource:
    return WikidataLinkedOntologyEdgeSource(
        linked_transformer="loc_concepts",
        event=ExtractorEvent(
            pipeline_date="test",
            environment="prod",
            transformer_type="wikidata_linked_loc_concepts",
            entity_type="edges",
        ),
    )


@pytest.fixture
def names_source() -> WikidataLinkedOntologyEdgeSource:
    return WikidataLinkedOntologyEdgeSource(
        linked_transformer="loc_names",
        event=ExtractorEvent(
            pipeline_date="test",
            environment="prod",
            transformer_type="wikidata_linked_loc_names",
            entity_type="edges",
        ),
    )


def _collect_streamed_edge_types(
    source: WikidataLinkedOntologyEdgeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> set[WikidataEdgeQueryType]:
    """Call stream_raw with a no-op _stream_all_edges_by_type and return which edge types were requested."""
    streamed: list[WikidataEdgeQueryType] = []

    def mock_stream(
        self: WikidataLinkedOntologyEdgeSource, edge_type: WikidataEdgeQueryType
    ) -> Generator[dict]:
        streamed.append(edge_type)
        yield from []

    monkeypatch.setattr(
        WikidataLinkedOntologyEdgeSource, "_stream_all_edges_by_type", mock_stream
    )
    monkeypatch.setattr(source, "_is_id_valid_for_transformer", lambda *a: False)
    monkeypatch.setattr(source, "_is_id_valid_for_ontology", lambda *a: True)

    list(source.stream_raw())
    return set(streamed)


def _patch_stream_and_validators(
    source: WikidataLinkedOntologySource,
    monkeypatch: pytest.MonkeyPatch,
    edges_by_type: dict[WikidataEdgeQueryType, list[dict]],
    valid_for_transformer: dict[str, set[str]],
    valid_for_ontology: dict[str, set[str]],
) -> None:
    """Patch _stream_all_edges_by_type and validators on a WikidataLinkedOntologySource."""

    def mock_stream(
        self: WikidataLinkedOntologySource, edge_type: WikidataEdgeQueryType
    ) -> Generator[dict]:
        yield from edges_by_type.get(edge_type, [])

    monkeypatch.setattr(
        WikidataLinkedOntologySource, "_stream_all_edges_by_type", mock_stream
    )
    monkeypatch.setattr(
        source,
        "_is_id_valid_for_transformer",
        lambda item_id, transformer: item_id
        in valid_for_transformer.get(transformer, set()),
    )
    monkeypatch.setattr(
        source,
        "_is_id_valid_for_ontology",
        lambda item_id, ontology: item_id in valid_for_ontology.get(ontology, set()),
    )


def test_filters_edges_by_from_id(
    concepts_source: WikidataLinkedOntologyEdgeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Only edges whose from_id is valid for the transformer are yielded."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={
            "instance_of": [
                {"from_id": "Q1", "to_id": "Q2"},
                {"from_id": "Q9", "to_id": "Q2"},  # Q9 not valid for transformer
                {"from_id": "Q3", "to_id": "Q2"},
            ],
        },
        valid_for_transformer={"wikidata_linked_loc_concepts": {"Q1", "Q3"}},
        valid_for_ontology={"wikidata": {"Q1", "Q2", "Q3", "Q9"}},
    )

    result = list(concepts_source.stream_raw())
    assert result == [
        ({"from_id": "Q1", "to_id": "Q2"}, "instance_of"),
        ({"from_id": "Q3", "to_id": "Q2"}, "instance_of"),
    ]


def test_no_edges_when_none_match_transformer(
    concepts_source: WikidataLinkedOntologyEdgeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Zero edges emitted when no from_ids are valid."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={
            "instance_of": [{"from_id": "Q99", "to_id": "Q2"}],
        },
        valid_for_transformer={},
        valid_for_ontology={"wikidata": {"Q2"}},
    )

    assert list(concepts_source.stream_raw()) == []


def test_same_as_edges_filtered_by_linked_transformer(
    concepts_source: WikidataLinkedOntologyEdgeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """SAME_AS edges are only yielded if to_id is valid for the linked_transformer."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={
            "same_as_loc": [
                {"from_id": "Q1", "to_id": "sh001"},  # valid concept
                {"from_id": "Q1", "to_id": "n001"},  # name, not a concept
            ],
        },
        valid_for_transformer={
            "wikidata_linked_loc_concepts": {"Q1"},
            "loc_names": {"n001"},
            "loc_concepts": {"sh001"},
        },
        valid_for_ontology={"wikidata": {"Q1"}, "loc": {"n001", "sh001"}},
    )

    result = list(concepts_source.stream_raw())
    assert result == [
        ({"from_id": "Q1", "to_id": "sh001"}, "same_as_loc"),
    ]


def test_same_as_edge_type_derived_from_linked_ontology(
    concepts_source: WikidataLinkedOntologyEdgeSource,
) -> None:
    """same_as_edge_type is 'same_as_loc' for a loc-linked source."""
    assert concepts_source.same_as_edge_type == "same_as_loc"


def test_internal_edges_filtered_by_wikidata_ontology(
    concepts_source: WikidataLinkedOntologyEdgeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Internal edges are only yielded if to_id is a known wikidata node."""
    _patch_stream_and_validators(
        concepts_source,
        monkeypatch,
        edges_by_type={
            "instance_of": [
                {"from_id": "Q1", "to_id": "Q2"},  # Q2 is known
                {"from_id": "Q1", "to_id": "Q99"},  # Q99 is unknown
            ],
        },
        valid_for_transformer={"wikidata_linked_loc_concepts": {"Q1"}},
        valid_for_ontology={"wikidata": {"Q1", "Q2"}},
    )

    result = list(concepts_source.stream_raw())
    assert result == [
        ({"from_id": "Q1", "to_id": "Q2"}, "instance_of"),
    ]


def test_concepts_edge_types_exclude_names_only(
    concepts_source: WikidataLinkedOntologyEdgeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Concepts source does not stream has_field_of_work or people relationship edges."""
    streamed = _collect_streamed_edge_types(concepts_source, monkeypatch)
    names_only_types = {
        "has_field_of_work",
        "has_father",
        "has_mother",
        "has_sibling",
        "has_spouse",
        "has_child",
    }
    assert names_only_types.isdisjoint(streamed)


def test_names_edge_types_include_field_of_work_and_relationships(
    names_source: WikidataLinkedOntologyEdgeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Names source includes has_field_of_work and people relationship edge types."""
    streamed = _collect_streamed_edge_types(names_source, monkeypatch)
    names_only_types = {
        "has_field_of_work",
        "has_father",
        "has_mother",
        "has_sibling",
        "has_spouse",
        "has_child",
    }
    assert names_only_types.issubset(streamed)


def test_both_concepts_and_names_include_parent_and_founder_types(
    concepts_source: WikidataLinkedOntologyEdgeSource,
    names_source: WikidataLinkedOntologyEdgeSource,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Both concepts and names always include parent, has_industry, and has_founder types."""
    common_types = {"instance_of", "subclass_of", "has_industry", "has_founder"}
    assert common_types.issubset(
        _collect_streamed_edge_types(concepts_source, monkeypatch)
    )
    assert common_types.issubset(
        _collect_streamed_edge_types(names_source, monkeypatch)
    )
