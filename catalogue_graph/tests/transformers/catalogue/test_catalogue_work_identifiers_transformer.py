from models.graph_edge import (
    WorkHasIdentifier,
    WorkHasIdentifierAttributes,
    WorkIdentifierHasParent,
)
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


def test_catalogue_work_identifiers_transformer_edges() -> None:
    add_mock_denormalised_documents()

    transformer = CatalogueWorkIdentifiersTransformer(None, True)
    edges = list(transformer._stream_edges())

    assert len(edges) == 15

    expected_has_source_identifier_edges = [
        ("m4u8drnu", "sierra-system-number||b15697551"),
        ("ydz8wd5r", "sierra-system-number||b15697290"),
        ("f33w7jru", "sierra-system-number||b15697423"),
    ]
    for start, end in expected_has_source_identifier_edges:
        check_bulk_load_edge(
            edges,
             WorkHasIdentifier(
                from_id=start,
                to_id=end,
                attributes=WorkHasIdentifierAttributes(
                    referenced_in="sourceIdentifier",
                ),
            )
        )

    expected_has_other_identifiers_edges = [
        ("m4u8drnu", "sierra-identifier||1569755"),
        ("m4u8drnu", "iconographic-number||569755i"),
        ("m4u8drnu", "miro-image-number||V0008815"),
    ]
    for start, end in expected_has_other_identifiers_edges:
        check_bulk_load_edge(
            edges,
            WorkHasIdentifier(
                from_id=start,
                to_id=end,
                attributes=WorkHasIdentifierAttributes(
                    referenced_in="otherIdentifiers",
                ),
            )
        )        

    expected_identifier_parent_edges = [
        ("iconographic-number||569742i", "iconographic-number||147150i"),
        ("iconographic-number||569729i", "iconographic-number||147150i"),
        ("iconographic-number||569755i", "iconographic-number||147150i"),
    ]
    for start, end in expected_identifier_parent_edges:
        check_bulk_load_edge(
            edges,
            WorkIdentifierHasParent(
                from_type="WorkIdentifier",
                to_type="WorkIdentifier",
                from_id=start,
                to_id=end,
                relationship="HAS_PARENT",
                directed=True,
            ),
        )
