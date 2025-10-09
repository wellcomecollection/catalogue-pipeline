from test_mocks import mock_es_secrets
from test_utils import add_mock_denormalised_documents, check_bulk_load_edge

from models.events import BasePipelineEvent
from models.graph_edge import (
    PathIdentifierHasParent,
    WorkHasPathIdentifier,
)
from models.graph_node import PathIdentifier
from transformers.catalogue.work_identifiers_transformer import (
    CatalogueWorkIdentifiersTransformer,
)

MOCK_EVENT = BasePipelineEvent(pipeline_date="dev")


def test_catalogue_work_identifiers_transformer_nodes() -> None:
    mock_es_secrets("graph_extractor", "dev")
    add_mock_denormalised_documents()

    transformer = CatalogueWorkIdentifiersTransformer(MOCK_EVENT, "public")
    nodes = list(transformer._stream_nodes())

    assert len(nodes) == 3

    expected_sierra_identifier = PathIdentifier(id="569742i", label=None)
    assert any(node == expected_sierra_identifier for node in nodes)

    expected_miro_image_number = PathIdentifier(id="569755i", label=None)
    assert any(node == expected_miro_image_number for node in nodes)

    expected_sierra_system_number = PathIdentifier(id="569729i", label=None)
    assert any(node == expected_sierra_system_number for node in nodes)


def test_catalogue_work_identifiers_transformer_edges() -> None:
    mock_es_secrets("graph_extractor", "dev")

    add_mock_denormalised_documents()

    transformer = CatalogueWorkIdentifiersTransformer(MOCK_EVENT, "public")
    edges = list(transformer._stream_edges())

    assert len(edges) == 6

    expected_has_path_identifier_edges = [
        ("f33w7jru", "569742i"),
        ("m4u8drnu", "569755i"),
        ("ydz8wd5r", "569729i"),
    ]
    for start, end in expected_has_path_identifier_edges:
        check_bulk_load_edge(
            edges,
            WorkHasPathIdentifier(
                from_id=start,
                to_id=end,
            ),
        )

    expected_identifier_parent_edges = [
        ("569729i", "147150i"),
        ("569742i", "147150i"),
        ("569755i", "147150i"),
    ]
    for start, end in expected_identifier_parent_edges:
        check_bulk_load_edge(
            edges,
            PathIdentifierHasParent(
                from_type="PathIdentifier",
                to_type="PathIdentifier",
                from_id=start,
                to_id=end,
                relationship="HAS_PARENT",
                directed=True,
            ),
        )
