from graph.transformers.catalogue.work_identifiers_transformer import (
    CatalogueWorkIdentifiersTransformer,
)
from models.events import BasePipelineEvent
from models.graph_edge import (
    PathIdentifierHasParent,
    WorkHasPathIdentifier,
)
from models.graph_node import PathIdentifier
from tests.mocks import MockElasticsearchClient, get_mock_es_client
from tests.test_utils import add_mock_merged_documents, check_bulk_load_edge

MOCK_EVENT = BasePipelineEvent(pipeline_date="dev")


def get_transformer(
    event: BasePipelineEvent | None = None,
) -> CatalogueWorkIdentifiersTransformer:
    event = event or MOCK_EVENT
    es_client = get_mock_es_client("graph_extractor", event.pipeline_date)
    return CatalogueWorkIdentifiersTransformer(event, es_client)


def test_catalogue_work_identifiers_transformer_nodes() -> None:
    add_mock_merged_documents(work_status="Visible")

    nodes = list(get_transformer()._stream_nodes())

    assert len(nodes) == 3

    expected_sierra_identifier = PathIdentifier(id="569742i", label=None)
    assert any(node == expected_sierra_identifier for node in nodes)

    expected_miro_image_number = PathIdentifier(id="569755i", label=None)
    assert any(node == expected_miro_image_number for node in nodes)

    expected_sierra_system_number = PathIdentifier(id="569729i", label=None)
    assert any(node == expected_sierra_system_number for node in nodes)


def test_catalogue_work_identifiers_transformer_edges() -> None:
    add_mock_merged_documents(work_status="Visible")

    edges = list(get_transformer()._stream_edges())

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


def _add_parent_work() -> None:
    """Add a parent work whose children are the works in the visible fixture."""
    parent_work = {
        "data": {
            "title": "Parent collection",
            "workType": "Standard",
            "otherIdentifiers": [
                {
                    "value": "147150i",
                    "ontologyType": "Work",
                    "identifierType": {"id": "calm-ref-no"},
                }
            ],
            "collectionPath": {"path": "147150i"},
        },
        "state": {
            "canonicalId": "parent01",
            "sourceIdentifier": {
                "value": "147150i",
                "identifierType": {"id": "calm-ref-no"},
            },
        },
        "type": "Visible",
    }
    MockElasticsearchClient.index("works-denormalised-dev", "parent01", parent_work)


def test_children_are_retrieved_when_scoped_to_parent_ids() -> None:
    """When the event is scoped to specific IDs (incremental mode), the source
    should also retrieve direct children of the streamed works."""
    _add_parent_work()
    add_mock_merged_documents(work_status="Visible")

    scoped_event = BasePipelineEvent(pipeline_date="dev", ids=["parent01"])
    transformer = get_transformer(scoped_event)

    nodes = list(transformer._stream_nodes())

    # The parent itself plus 3 children from the visible fixture
    assert len(nodes) == 4

    expected_parent = PathIdentifier(id="147150i", label=None)
    assert any(node == expected_parent for node in nodes)

    expected_children = [
        PathIdentifier(id="569742i", label=None),
        PathIdentifier(id="569755i", label=None),
        PathIdentifier(id="569729i", label=None),
    ]
    for expected in expected_children:
        assert any(node == expected for node in nodes)


def test_children_edges_are_created_when_scoped_to_parent_ids() -> None:
    """When streaming children alongside their parent, the correct
    PathIdentifierHasParent edges should be emitted."""
    _add_parent_work()
    add_mock_merged_documents(work_status="Visible")

    scoped_event = BasePipelineEvent(pipeline_date="dev", ids=["parent01"])
    transformer = get_transformer(scoped_event)

    edges = list(transformer._stream_edges())

    # Parent: 1 WorkHasPathIdentifier (no parent path since path has no '/')
    # 3 children: each has 1 WorkHasPathIdentifier + 1 PathIdentifierHasParent = 6
    # Total: 7 edges
    assert len(edges) == 7

    # Parent work -> its path identifier
    check_bulk_load_edge(
        edges,
        WorkHasPathIdentifier(from_id="parent01", to_id="147150i"),
    )

    # Each child work -> its path identifier
    for work_id, path_id in [
        ("f33w7jru", "569742i"),
        ("m4u8drnu", "569755i"),
        ("ydz8wd5r", "569729i"),
    ]:
        check_bulk_load_edge(
            edges,
            WorkHasPathIdentifier(from_id=work_id, to_id=path_id),
        )

    # Each child path identifier -> parent path identifier
    for child_path_id in ["569729i", "569742i", "569755i"]:
        check_bulk_load_edge(
            edges,
            PathIdentifierHasParent(
                from_id=child_path_id,
                to_id="147150i",
            ),
        )
