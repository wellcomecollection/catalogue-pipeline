from test_utils import add_mock_denormalised_documents

from models.graph_edge import BaseEdge, WorkHasConcept, WorkHasConceptAttributes
from models.graph_node import Work
from transformers.catalogue.works_transformer import CatalogueWorksTransformer


def _check_edge(
    all_edges: list[BaseEdge], from_id: str, to_id: str, expected_edge: BaseEdge
) -> None:
    filtered_edges = [
        edge for edge in all_edges if edge.from_id == from_id and edge.to_id == to_id
    ]
    assert len(filtered_edges) == 1
    assert filtered_edges[0] == expected_edge


def test_catalogue_works_transformer_nodes() -> None:
    add_mock_denormalised_documents()

    transformer = CatalogueWorksTransformer(None, True)
    nodes = list(transformer._stream_nodes())

    assert len(nodes) == 3
    expected_work = Work(
        id="m4u8drnu",
        label="Human skull, seen from below, with details of the lower jaw bone. Etching by Martin after J. Gamelin, 1778/1779.",
        type="Work",
        alternative_labels=[],
    )
    assert any(node == expected_work for node in nodes)


def test_catalogue_works_transformer_edges() -> None:
    add_mock_denormalised_documents()

    transformer = CatalogueWorksTransformer(None, True)
    edges = list(transformer._stream_edges())

    assert len(edges) == 15

    _check_edge(
        edges,
        "m4u8drnu",
        "s6s24vd7",
        WorkHasConcept(
            from_type="Work",
            to_type="Concept",
            from_id="m4u8drnu",
            to_id="s6s24vd7",
            relationship="HAS_CONCEPT",
            directed=True,
            attributes=WorkHasConceptAttributes(
                referenced_in="subjects", referenced_type="Subject"
            ),
        ),
    )

    _check_edge(
        edges,
        "ydz8wd5r",
        "s6s24vd8",
        WorkHasConcept(
            from_type="Work",
            to_type="Concept",
            from_id="ydz8wd5r",
            to_id="s6s24vd8",
            relationship="HAS_CONCEPT",
            directed=True,
            attributes=WorkHasConceptAttributes(
                referenced_in="subjects", referenced_type="Subject"
            ),
        ),
    )

    _check_edge(
        edges,
        "ydz8wd5r",
        "yfqryj26",
        WorkHasConcept(
            from_type="Work",
            to_type="Concept",
            from_id="ydz8wd5r",
            to_id="yfqryj26",
            relationship="HAS_CONCEPT",
            directed=True,
            attributes=WorkHasConceptAttributes(
                referenced_in="genres", referenced_type="Genre"
            ),
        ),
    )

    _check_edge(
        edges,
        "ydz8wd5r",
        "uykuavkt",
        WorkHasConcept(
            from_type="Work",
            to_type="Concept",
            from_id="ydz8wd5r",
            to_id="uykuavkt",
            relationship="HAS_CONCEPT",
            directed=True,
            attributes=WorkHasConceptAttributes(
                referenced_in="contributors", referenced_type="Person"
            ),
        ),
    )
