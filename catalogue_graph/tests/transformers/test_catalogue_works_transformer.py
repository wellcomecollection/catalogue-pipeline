from test_mocks import MockRequest
from test_utils import load_fixture

from config import CATALOGUE_SNAPSHOT_URL
from models.graph_edge import WorkHasConcept, WorkHasConceptAttributes
from models.graph_node import Work
from transformers.catalogue.works_transformer import CatalogueWorksTransformer


def _add_catalogue_request() -> None:
    MockRequest.mock_responses(
        [
            {
                "method": "GET",
                "url": CATALOGUE_SNAPSHOT_URL,
                "status_code": 200,
                "json_data": None,
                "content_bytes": load_fixture("catalogue_example.json"),
                "params": None,
            }
        ]
    )


def test_catalogue_concepts_transformer_nodes() -> None:
    _add_catalogue_request()

    catalogue_works_transformer = CatalogueWorksTransformer(CATALOGUE_SNAPSHOT_URL)

    nodes = list(catalogue_works_transformer._stream_nodes())

    assert len(nodes) == 4
    assert nodes[0] == Work(
        id="m4u8drnu",
        label="Human skull, seen from below, with details of the lower jaw bone. Etching by Martin after J. Gamelin, 1778/1779.",
        type="Work",
        alternative_labels=[],
    )


def test_catalogue_concepts_transformer_edges() -> None:
    _add_catalogue_request()

    catalogue_works_transformer = CatalogueWorksTransformer(CATALOGUE_SNAPSHOT_URL)

    edges = list(catalogue_works_transformer._stream_edges())

    assert len(edges) == 16
    assert edges[0] == WorkHasConcept(
        from_type="Work",
        to_type="Concept",
        from_id="m4u8drnu",
        to_id="s6s24vd7",
        relationship="HAS_CONCEPT",
        directed=True,
        attributes=WorkHasConceptAttributes(referenced_in="subjects"),
    )
    assert edges[5] == WorkHasConcept(
        from_type="Work",
        to_type="Concept",
        from_id="ydz8wd5r",
        to_id="s6s24vd8",
        relationship="HAS_CONCEPT",
        directed=True,
        attributes=WorkHasConceptAttributes(referenced_in="subjects"),
    )
    assert edges[6] == WorkHasConcept(
        from_type="Work",
        to_type="Concept",
        from_id="ydz8wd5r",
        to_id="yfqryj26",
        relationship="HAS_CONCEPT",
        directed=True,
        attributes=WorkHasConceptAttributes(referenced_in="genres"),
    )
    assert edges[7] == WorkHasConcept(
        from_type="Work",
        to_type="Concept",
        from_id="ydz8wd5r",
        to_id="uykuavkt",
        relationship="HAS_CONCEPT",
        directed=True,
        attributes=WorkHasConceptAttributes(referenced_in="contributors"),
    )
