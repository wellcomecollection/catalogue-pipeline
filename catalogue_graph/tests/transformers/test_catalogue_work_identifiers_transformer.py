from models.graph_edge import WorkIdentifierHasParent
from models.graph_node import WorkIdentifier
from test_utils import add_mock_denormalised_documents, check_bulk_load_edge
from transformers.catalogue.work_identifiers_transformer import (
    CatalogueWorkIdentifiersTransformer,
)


def test_catalogue_work_identifiers_transformer_nodes() -> None:
    add_mock_denormalised_documents()

    transformer = CatalogueWorkIdentifiersTransformer(None, True)
    nodes = list(transformer._stream_nodes())

    assert len(nodes) == 12

    expected_sierra_identifier = WorkIdentifier(
        id="sierra-identifier||1569742", label="sierra-identifier", identifier="1569742"
    )
    assert any(node == expected_sierra_identifier for node in nodes)

    expected_iconographic_number = WorkIdentifier(
        id="iconographic-number||569729i",
        label="iconographic-number",
        identifier="569729i",
    )
    assert any(node == expected_iconographic_number for node in nodes)

    expected_miro_image_number = WorkIdentifier(
        id="miro-image-number||V0008815",
        label="miro-image-number",
        identifier="V0008815",
    )
    assert any(node == expected_miro_image_number for node in nodes)

    expected_sierra_system_number = WorkIdentifier(
        id="sierra-system-number||b15697551",
        label="sierra-system-number",
        identifier="b15697551",
    )
    assert any(node == expected_sierra_system_number for node in nodes)


def test_catalogue_works_transformer_edges() -> None:
    add_mock_denormalised_documents()

    transformer = CatalogueWorkIdentifiersTransformer(None, True)
    edges = list(transformer._stream_edges())

    assert len(edges) == 3

    expected_edges = [
        ("iconographic-number||569742i", "iconographic-number||147150i"),
        ("iconographic-number||569729i", "iconographic-number||147150i"),
        ("iconographic-number||569755i", "iconographic-number||147150i"),
    ]
    for expected_start, expected_end in expected_edges:
        check_bulk_load_edge(
            edges,
            WorkIdentifierHasParent(
                from_type="WorkIdentifier",
                to_type="WorkIdentifier",
                from_id=expected_start,
                to_id=expected_end,
                relationship="HAS_PARENT",
                directed=True,
            ),
        )
