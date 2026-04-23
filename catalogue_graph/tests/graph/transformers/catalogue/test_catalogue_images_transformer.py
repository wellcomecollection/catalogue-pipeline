import pytest

from graph.transformers.catalogue.images_transformer import (
    CatalogueImagesTransformer,
)
from models.events import BasePipelineEvent
from models.graph_edge import WorkHasImage
from models.graph_node import Image
from tests.mocks import MockElasticsearchClient, get_mock_es_client

MOCK_EVENT = BasePipelineEvent(pipeline_date="dev")

VALID_RAW_IMAGE = {
    "state": {"canonicalId": "img123"},
    "source": {"id": {"canonicalId": "work456"}},
    "locations": [
        {
            "url": "https://iiif.wellcomecollection.org/image/V001/info.json",
            "locationType": {"id": "iiif-image"},
            "license": {"id": "cc-by"},
            "accessConditions": [
                {"method": {"type": "ViewOnline"}, "status": {"type": "Open"}}
            ],
            "type": "DigitalLocation",
        }
    ],
}


def get_transformer() -> CatalogueImagesTransformer:
    es_client = get_mock_es_client("graph_extractor", MOCK_EVENT.pipeline_date)
    return CatalogueImagesTransformer(MOCK_EVENT, es_client)


def mock_es_image(image_id: str, raw_image: dict) -> None:
    MockElasticsearchClient.index("images-augmented-dev", image_id, raw_image)


def test_transform_node() -> None:
    transformer = get_transformer()

    node = transformer.transform_node(VALID_RAW_IMAGE)

    assert node == Image(
        id="img123",
        location_type="iiif-image",
        location_url="https://iiif.wellcomecollection.org/image/V001/info.json",
    )


def test_extract_edges() -> None:
    transformer = get_transformer()

    edges = list(transformer.extract_edges(VALID_RAW_IMAGE))

    assert edges == [
        WorkHasImage(
            from_id="work456",
            to_id="img123",
        )
    ]


def test_transform_node_with_multiple_locations() -> None:
    """When an image has multiple locations, the iiif-image one is used."""
    raw_image = {
        "state": {"canonicalId": "img789"},
        "source": {"id": {"canonicalId": "workABC"}},
        "locations": [
            {
                "url": "https://example.com/thumbnail.jpg",
                "locationType": {"id": "thumbnail-image"},
                "license": {"id": "cc-by"},
                "accessConditions": [
                    {"method": {"type": "ViewOnline"}, "status": {"type": "Open"}}
                ],
                "type": "DigitalLocation",
            },
            {
                "url": "https://iiif.wellcomecollection.org/image/V002/info.json",
                "locationType": {"id": "iiif-image"},
                "license": {"id": "cc-by"},
                "accessConditions": [
                    {"method": {"type": "ViewOnline"}, "status": {"type": "Open"}}
                ],
                "type": "DigitalLocation",
            },
        ],
    }

    transformer = get_transformer()
    node = transformer.transform_node(raw_image)

    assert node == Image(
        id="img789",
        location_type="iiif-image",
        location_url="https://iiif.wellcomecollection.org/image/V002/info.json",
    )


def test_transform_node_raises_on_missing_iiif_location() -> None:
    """Raises ValueError when no iiif-image location is present."""
    raw_image = {
        "state": {"canonicalId": "img_no_iiif"},
        "source": {"id": {"canonicalId": "work999"}},
        "locations": [
            {
                "url": "https://example.com/thumbnail.jpg",
                "locationType": {"id": "thumbnail-image"},
                "license": {"id": "cc-by"},
                "accessConditions": [
                    {"method": {"type": "ViewOnline"}, "status": {"type": "Open"}}
                ],
                "type": "DigitalLocation",
            },
        ],
    }

    transformer = get_transformer()

    with pytest.raises(ValueError, match="does not have a IIIF image location"):
        transformer.transform_node(raw_image)


def test_transform_node_raises_on_unexpected_access_conditions() -> None:
    """Raises ValueError when iiif-image has unexpected access conditions."""
    raw_image = {
        "state": {"canonicalId": "img_bad_access"},
        "source": {"id": {"canonicalId": "work999"}},
        "locations": [
            {
                "url": "https://iiif.wellcomecollection.org/image/V003/info.json",
                "locationType": {"id": "iiif-image"},
                "license": {"id": "cc-by"},
                "accessConditions": [
                    {
                        "method": {"type": "ViewOnline"},
                        "status": {"type": "Restricted"},
                    }
                ],
                "type": "DigitalLocation",
            },
        ],
    }

    transformer = get_transformer()

    with pytest.raises(ValueError, match="Unexpected access conditions"):
        transformer.transform_node(raw_image)


def test_stream_nodes_from_es() -> None:
    """End-to-end: stream nodes from a mocked ES index."""
    mock_es_image("img123", VALID_RAW_IMAGE)

    transformer = get_transformer()
    nodes = list(transformer._stream_nodes())

    assert len(nodes) == 1
    assert nodes[0] == Image(
        id="img123",
        location_type="iiif-image",
        location_url="https://iiif.wellcomecollection.org/image/V001/info.json",
    )


def test_stream_edges_from_es() -> None:
    """End-to-end: stream edges from a mocked ES index."""
    mock_es_image("img123", VALID_RAW_IMAGE)

    transformer = get_transformer()
    edges = list(transformer._stream_edges())

    assert len(edges) == 1
    assert edges[0] == WorkHasImage(from_id="work456", to_id="img123")
