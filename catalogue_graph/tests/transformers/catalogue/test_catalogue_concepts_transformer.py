from models.events import BasePipelineEvent
from models.graph_edge import (
    ConceptHasSourceConcept,
    ConceptHasSourceConceptAttributes,
)
from models.graph_node import Concept
from tests.mocks import get_mock_es_client
from tests.test_utils import (
    add_mock_merged_documents,
    add_mock_transformer_outputs_for_ontologies,
    check_bulk_load_edge,
)
from transformers.catalogue.concepts_transformer import CatalogueConceptsTransformer


def get_transformer(pipeline_date: str = "dev") -> CatalogueConceptsTransformer:
    es_client = get_mock_es_client("graph_extractor", pipeline_date)
    return CatalogueConceptsTransformer(
        BasePipelineEvent(pipeline_date=pipeline_date), es_client
    )


def test_catalogue_concepts_transformer_nodes() -> None:
    add_mock_transformer_outputs_for_ontologies(["loc", "mesh"])
    add_mock_merged_documents(work_status="Visible")

    nodes = list(get_transformer()._stream_nodes())

    assert len(nodes) == 12
    assert any(
        item == Concept(id="s6s24vd7", label="Human anatomy", source="lc-subjects")
        for item in nodes
    )


def test_catalogue_concepts_transformer_edges() -> None:
    pipeline_date = "2027-12-24"
    add_mock_transformer_outputs_for_ontologies(["loc", "mesh"], pipeline_date)
    add_mock_merged_documents(pipeline_date, work_status="Visible")

    edges = list(get_transformer(pipeline_date)._stream_edges())
    assert len(edges) == 7

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


def test_mismatched_pipeline_date() -> None:
    pipeline_date = "2027-12-24"
    add_mock_transformer_outputs_for_ontologies(["loc", "mesh"], pipeline_date)

    # Works exist in an index with a different pipeline date
    add_mock_merged_documents("2025-01-01", work_status="Visible")

    edges = list(get_transformer(pipeline_date=pipeline_date)._stream_edges())
    assert len(edges) == 0
