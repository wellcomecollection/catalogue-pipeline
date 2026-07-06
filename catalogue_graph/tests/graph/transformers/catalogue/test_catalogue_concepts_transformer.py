import csv
import io

from graph.transformers.catalogue.concepts_transformer import (
    CatalogueConceptsTransformer,
)
from models.events import BasePipelineEvent
from models.graph_edge import (
    ConceptHasSourceConcept,
    ConceptHasSourceConceptAttributes,
)
from models.graph_node import Concept, SourceConceptStub
from tests.mocks import get_mock_es_client
from tests.test_utils import (
    add_mock_merged_documents,
    add_mock_transformer_outputs_for_ontologies,
    check_bulk_load_edge,
)


def get_transformer(pipeline_date: str = "dev") -> CatalogueConceptsTransformer:
    es_client = get_mock_es_client("graph_extractor", pipeline_date)
    return CatalogueConceptsTransformer(
        BasePipelineEvent(pipeline_date=pipeline_date), es_client
    )


def test_catalogue_concepts_transformer_nodes() -> None:
    add_mock_transformer_outputs_for_ontologies(["loc", "mesh"])
    add_mock_merged_documents(work_status="Visible")

    nodes = list(get_transformer()._stream_nodes())

    concepts = [item for item in nodes if isinstance(item, Concept)]
    stubs = [item for item in nodes if isinstance(item, SourceConceptStub)]

    assert len(concepts) == 12
    assert any(
        item == Concept(id="s6s24vd7", label="Human anatomy", source="lc-subjects")
        for item in concepts
    )

    # A stub node is emitted for every matched source concept, with the original-case
    # label from the source ontology's bulk load file
    assert any(
        item
        == SourceConceptStub(id="sh85045046", label="Etchings", source="lc-subjects")
        for item in stubs
    )


def test_catalogue_concepts_transformer_stub_nodes() -> None:
    pipeline_date = "2027-12-24"
    add_mock_transformer_outputs_for_ontologies(["loc", "mesh"], pipeline_date)
    add_mock_merged_documents(pipeline_date, work_status="Visible")

    stubs = [
        item
        for item in get_transformer(pipeline_date)._stream_nodes()
        if isinstance(item, SourceConceptStub)
    ]

    # Re-register the mocked ontology files consumed by the nodes run above
    add_mock_transformer_outputs_for_ontologies(["loc", "mesh"], pipeline_date)
    edges = list(get_transformer(pipeline_date)._stream_edges())

    # Every edge target has a corresponding stub node, so edge bulk loads cannot
    # reference a vertex which does not exist
    assert {stub.id for stub in stubs} == {edge.to_id for edge in edges}

    # One stub per distinct source id, even when multiple concepts match it
    # (both s6s24vd8 and s6s24vd9 match D000715)
    assert sum(1 for stub in stubs if stub.id == "D000715") == 1
    assert any(
        item == SourceConceptStub(id="D000715", label="Anatomy", source="nlm-mesh")
        for item in stubs
    )

    # Stubs carry the same node label as the full node which later enriches them
    for stub in stubs:
        if stub.source == "nlm-mesh" or stub.id.startswith("s"):
            assert type(stub).bulk_load_label() in ("SourceConcept", "SourceLocation")
        if stub.id.startswith("n"):
            assert type(stub).bulk_load_label() in ("SourceName", "SourceLocation")


def test_catalogue_concepts_transformer_bulk_load_csv() -> None:
    add_mock_transformer_outputs_for_ontologies(["loc", "mesh"])
    add_mock_merged_documents(work_status="Visible")

    file = io.StringIO()
    get_transformer()._stream_to_bulk_load_file(file, "nodes")
    rows = list(csv.DictReader(io.StringIO(file.getvalue())))

    # Concept and stub rows share a single header but carry different node labels
    assert set(rows[0].keys()) == {
        ":ID",
        ":LABEL",
        "id:String",
        "label:String",
        "source:String",
    }
    labels = {row[":LABEL"] for row in rows}
    assert "Concept" in labels
    assert "SourceConcept" in labels
    assert all(row[":ID"] for row in rows)


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
