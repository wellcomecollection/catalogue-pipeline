import json
import math

import pytest
from test_utils import load_fixture
from test_wikidata_concepts_source import (
    _add_mock_loc_transformer_outputs,
    _add_mock_wikidata_requests,
)

from models.graph_edge import SourceConceptSameAs
from models.graph_node import SourceConcept
from transformers.wikidata.concepts_transformer import WikidataConceptsTransformer
from transformers.wikidata.raw_concept import RawWikidataLocation, RawWikidataName


def test_wikidata_concepts_nodes_transformer() -> None:
    _add_mock_loc_transformer_outputs()
    _add_mock_wikidata_requests("nodes")

    transformer = WikidataConceptsTransformer(
        entity_type="nodes", linked_ontology="loc"
    )

    nodes = list(transformer._stream_entities(entity_type="nodes"))

    assert len(list(nodes)) == 4

    assert nodes[0] == SourceConcept(
        id="Q1",
        label="Eustigmatophyceae",
        source="wikidata",
        alternative_ids=[],
        alternative_labels=[],
        description="class of algae",
    )


def test_wikidata_concepts_edges_transformer() -> None:
    _add_mock_loc_transformer_outputs()
    _add_mock_wikidata_requests("edges")

    transformer = WikidataConceptsTransformer(
        entity_type="edges", linked_ontology="loc"
    )

    edges = list(transformer._stream_entities(entity_type="edges"))
    assert len(list(edges)) == 7

    assert edges[0] == SourceConceptSameAs(
        from_type="SourceConcept",
        to_type="SourceConcept",
        from_id="Q1",
        to_id="sh00000001",
        relationship="SAME_AS",
        directed=False,
        attributes={"source": "wikidata"},
    )

    assert edges[1] == SourceConceptSameAs(
        from_type="SourceConcept",
        to_type="SourceConcept",
        from_id="sh00000001",
        to_id="Q1",
        relationship="SAME_AS",
        directed=False,
        attributes={"source": "wikidata"},
    )


def test_wikidata_raw_location() -> None:
    raw_location_input = json.loads(load_fixture("wikidata/raw_location.json"))
    raw_location = RawWikidataLocation(raw_location_input)

    assert raw_location.coordinates["latitude"] is not None
    assert raw_location.coordinates["longitude"] is not None
    assert math.isclose(raw_location.coordinates["latitude"], 41.346111111)
    assert math.isclose(raw_location.coordinates["longitude"], -85.469166666)


def test_wikidata_raw_name() -> None:
    raw_name_input = json.loads(load_fixture("wikidata/raw_name.json"))
    raw_name = RawWikidataName(raw_name_input)

    assert raw_name.date_of_birth == "1949-01-28T00:00:00Z"
    assert raw_name.date_of_death == "2013-07-10T00:00:00Z"
    assert raw_name.place_of_birth == "Queens"
    assert raw_name.label == "Walter McCaffrey"
    assert raw_name.description == "American politician"


def test_wikidata_raw_location_empty_coordinates() -> None:
    raw_location = RawWikidataLocation({})
    assert raw_location.coordinates["latitude"] is None
    assert raw_location.coordinates["longitude"] is None


def test_wikidata_raw_location_uri_type_coordinates() -> None:
    raw_location = RawWikidataLocation({"type": "uri", "value": "some-url"})
    assert raw_location.coordinates["latitude"] is None
    assert raw_location.coordinates["longitude"] is None


def test_wikidata_raw_location_invalid_coordinates() -> None:
    raw_location = RawWikidataLocation(
        {
            "item": {"type": "uri", "value": "some-id"},
            "coordinates": {"type": "literal", "value": "invalid value"},
        }
    )
    with pytest.raises(AssertionError):
        _ = raw_location.coordinates["latitude"]

    with pytest.raises(AssertionError):
        _ = raw_location.coordinates["longitude"]


def test_wikidata_raw_name_invalid_date() -> None:
    raw_name = RawWikidataName(
        {
            "dateOfBirth": {"type": "literal", "value": "+0000-00-00T00:00:00Z"},
            "dateOfDeath": {"type": "literal", "value": "+0000-00-00T00:00:00Z"},
        },
    )
    assert raw_name.date_of_birth is None
    assert raw_name.date_of_death is None


def test_wikidata_raw_name_uri_type_date() -> None:
    raw_name = RawWikidataName(
        {
            "dateOfBirth": {"type": "uri", "value": "some-uri"},
            "dateOfDeath": {"type": "uri", "value": "some-uri"},
        }
    )
    assert raw_name.date_of_birth is None
    assert raw_name.date_of_death is None


def test_wikidata_raw_name_uri_date() -> None:
    raw_name = RawWikidataName(
        {
            "dateOfBirth": {"type": "literal", "value": "https://some-url"},
            "dateOfDeath": {"type": "literal", "value": "https://some-url"},
        }
    )
    assert raw_name.date_of_birth is None
    assert raw_name.date_of_death is None
