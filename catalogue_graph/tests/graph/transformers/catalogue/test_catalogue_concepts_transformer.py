import pytest

from graph.transformers.catalogue.concepts_transformer import (
    CatalogueConceptsTransformer,
)
from models.events import BasePipelineEvent
from models.graph_edge import (
    ConceptHasSourceConcept,
    ConceptHasSourceConceptAttributes,
)
from models.graph_node import Concept
from tests.mocks import MockElasticsearchClient, get_mock_es_client
from tests.test_utils import (
    add_mock_merged_documents,
    add_mock_transformer_outputs_for_ontologies,
    check_bulk_load_edge,
)


def get_transformer(
    pipeline_date: str = "dev", graph_date: str = "dev"
) -> CatalogueConceptsTransformer:
    es_client = get_mock_es_client("graph_extractor", pipeline_date)
    return CatalogueConceptsTransformer(
        BasePipelineEvent(pipeline_date=pipeline_date, graph_date=graph_date),
        es_client,
    )


def test_catalogue_concepts_transformer_nodes() -> None:
    add_mock_transformer_outputs_for_ontologies(["loc", "mesh", "weco"])
    add_mock_merged_documents(work_status="Visible")

    nodes = list(get_transformer()._stream_nodes())

    assert len(nodes) == 12
    assert any(
        item == Concept(id="s6s24vd7", label="Human anatomy", source="lc-subjects")
        for item in nodes
    )


def test_catalogue_concepts_transformer_edges() -> None:
    pipeline_date = "2027-12-24"
    graph_date = "2024-12-24"
    add_mock_transformer_outputs_for_ontologies(
        ["loc", "mesh", "weco"], pipeline_date, graph_date
    )
    add_mock_merged_documents(pipeline_date, work_status="Visible")

    edges = list(get_transformer(pipeline_date, graph_date)._stream_edges())
    assert len(edges) == 9

    check_bulk_load_edge(
        edges,
        ConceptHasSourceConcept(
            from_type="Concept",
            to_type="SourceConcept",
            from_id="s6s24vd7",
            to_id="sh85004839",
            relationship="HAS_SOURCE_CONCEPT",
            directed=True,
            attributes=ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="identifier"
            ),
        ),
    )

    check_bulk_load_edge(
        edges,
        ConceptHasSourceConcept(
            from_type="Concept",
            to_type="SourceConcept",
            from_id="yfqryj26",
            to_id="sh85045046",
            relationship="HAS_SOURCE_CONCEPT",
            directed=True,
            attributes=ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="label"
            ),
        ),
    )

    check_bulk_load_edge(
        edges,
        ConceptHasSourceConcept(
            from_type="Concept",
            to_type="SourceConcept",
            from_id="s6s24vd8",
            to_id="D000715",
            relationship="HAS_SOURCE_CONCEPT",
            directed=True,
            attributes=ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="identifier"
            ),
        ),
    )

    check_bulk_load_edge(
        edges,
        ConceptHasSourceConcept(
            from_type="Concept",
            to_type="SourceConcept",
            from_id="s6s24vd9",
            to_id="D000715",
            relationship="HAS_SOURCE_CONCEPT",
            directed=True,
            attributes=ConceptHasSourceConceptAttributes(
                qualifier="Q000266", matched_by="identifier"
            ),
        ),
    )

    # An authority override sits alongside another ontology's source concept, and the authority
    # record's blank label must not stop us matching it.
    check_bulk_load_edge(
        edges,
        ConceptHasSourceConcept(
            from_type="Concept",
            to_type="SourceConcept",
            from_id="s6s24vd7",
            to_id="weco:s6s24vd7",
            relationship="HAS_SOURCE_CONCEPT",
            directed=True,
            attributes=ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="identifier"
            ),
        ),
    )

    # A concept with no source concept in any other ontology still gets its override edge.
    check_bulk_load_edge(
        edges,
        ConceptHasSourceConcept(
            from_type="Concept",
            to_type="SourceConcept",
            from_id="kpeywdvq",
            to_id="weco:kpeywdvq",
            relationship="HAS_SOURCE_CONCEPT",
            directed=True,
            attributes=ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="identifier"
            ),
        ),
    )
    assert len([edge for edge in edges if edge.from_id == "kpeywdvq"]) == 1


def _label_derived_concept(label: str, concept_type: str) -> dict:
    return {
        "id": {
            "canonicalId": "cpindex1",
            "sourceIdentifier": {
                "identifierType": {"id": "label-derived"},
                "ontologyType": concept_type,
                "value": label.lower(),
            },
            "otherIdentifiers": [],
            "type": "Identified",
        },
        "label": label,
        "type": concept_type,
    }


def _add_mock_work(pipeline_date: str, work_id: str, data: dict) -> None:
    MockElasticsearchClient.index(
        f"works-denormalised-{pipeline_date}",
        work_id,
        {
            "state": {"canonicalId": work_id},
            "type": "Visible",
            "data": {"subjects": [], "contributors": [], "genres": [], **data},
        },
    )


@pytest.mark.parametrize("person_work_id", ["aaaaaaaa", "zzzzzzzz"])
def test_catalogue_concepts_transformer_matches_on_the_most_common_type(
    person_work_id: str,
) -> None:
    """
    "Consumer price index" is a MeSH alternative label, which the type guard refuses for a
    Person. The label is a Person on one work and a Concept on two, so the match must use
    Concept whichever work streams first (the mock client streams works in id order).
    """
    pipeline_date = "2027-12-24"
    graph_date = "2024-12-24"
    add_mock_transformer_outputs_for_ontologies(
        ["loc", "mesh", "weco"], pipeline_date, graph_date
    )

    label = "Consumer price index"
    subject = {**_label_derived_concept(label, "Concept")}
    subject["concepts"] = [_label_derived_concept(label, "Concept")]
    contributor = {
        "id": {"type": "Unidentifiable"},
        "agent": _label_derived_concept(label, "Person"),
        "roles": [],
        "primary": True,
    }

    _add_mock_work(pipeline_date, person_work_id, {"contributors": [contributor]})
    _add_mock_work(pipeline_date, "mmmmmmmm", {"subjects": [subject]})
    _add_mock_work(pipeline_date, "nnnnnnnn", {"subjects": [subject]})

    edges = list(get_transformer(pipeline_date, graph_date)._stream_edges())

    assert edges == [
        ConceptHasSourceConcept(
            from_id="cpindex1",
            to_id="D004467",
            attributes=ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="label"
            ),
        )
    ]


@pytest.mark.parametrize("clean_work_id", ["aaaaaaaa", "zzzzzzzz"])
def test_catalogue_concepts_transformer_matches_on_any_spelling(
    clean_work_id: str,
) -> None:
    """
    Spellings that fold to one label-derived id share it, so a mis-encoded spelling on most
    works must not stop the clean one matching, whichever work streams first.
    """
    pipeline_date = "2027-12-24"
    graph_date = "2024-12-24"
    add_mock_transformer_outputs_for_ontologies(
        ["loc", "mesh", "weco"], pipeline_date, graph_date
    )

    def subject(label: str) -> dict:
        return {
            **_label_derived_concept(label, "Concept"),
            "concepts": [_label_derived_concept(label, "Concept")],
        }

    _add_mock_work(pipeline_date, clean_work_id, {"subjects": [subject("Tacos")]})
    _add_mock_work(pipeline_date, "mmmmmmmm", {"subjects": [subject("Tac�os")]})
    _add_mock_work(pipeline_date, "nnnnnnnn", {"subjects": [subject("Tac�os")]})

    edges = list(get_transformer(pipeline_date, graph_date)._stream_edges())

    assert edges == [
        ConceptHasSourceConcept(
            from_id="cpindex1",
            to_id="sh00000002",
            attributes=ConceptHasSourceConceptAttributes(
                qualifier=None, matched_by="label"
            ),
        )
    ]


def test_mismatched_pipeline_date() -> None:
    pipeline_date = "2027-12-24"
    graph_date = "2027-12-24"
    add_mock_transformer_outputs_for_ontologies(
        ["loc", "mesh", "weco"], pipeline_date, graph_date
    )

    # Works exist in an index with a different pipeline date
    add_mock_merged_documents("2025-01-01", work_status="Visible")

    edges = list(get_transformer(pipeline_date, graph_date)._stream_edges())
    assert len(edges) == 0


def test_mismatched_graph_date() -> None:
    pipeline_date = "2027-12-24"
    graph_date = "2027-12-24"
    add_mock_transformer_outputs_for_ontologies(
        ["loc", "mesh", "weco"], pipeline_date, graph_date
    )

    add_mock_merged_documents("2027-12-24", work_status="Visible")

    # Transformer uses wrong graph date
    with pytest.raises(KeyError, match="does not exist"):
        list(get_transformer(pipeline_date, "2025-01-01")._stream_edges())
