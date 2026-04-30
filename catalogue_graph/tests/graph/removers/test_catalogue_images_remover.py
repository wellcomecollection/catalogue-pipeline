from unittest.mock import MagicMock

from graph.removers.catalogue_images_remover import CatalogueImagesGraphRemover
from models.events import IncrementalGraphRemoverEvent
from models.incremental_window import IncrementalWindow
from utils.types import EntityType


def _make_remover(
    entity_type: EntityType = "nodes",
) -> tuple[CatalogueImagesGraphRemover, MagicMock]:
    event = IncrementalGraphRemoverEvent(
        pipeline_date="2025-01-01",
        transformer_type="catalogue_images",
        entity_type=entity_type,
        window=IncrementalWindow.model_validate({"end_time": "2025-01-01T12:00"}),
    )
    neptune_client = MagicMock()
    return CatalogueImagesGraphRemover(event, neptune_client), neptune_client


def test_get_total_node_count() -> None:
    remover, neptune_client = _make_remover()
    neptune_client.get_total_node_count.return_value = 42

    assert remover.get_total_node_count() == 42
    neptune_client.get_total_node_count.assert_called_once_with("Image")


def test_get_total_edge_count() -> None:
    remover, _ = _make_remover()
    assert remover.get_total_edge_count() == 0


def test_get_node_ids_to_remove() -> None:
    remover, neptune_client = _make_remover()
    neptune_client.get_disconnected_node_ids.return_value = iter(
        ["img1", "img2", "img3"]
    )

    result = list(remover.get_node_ids_to_remove())

    assert result == ["img1", "img2", "img3"]
    neptune_client.get_disconnected_node_ids.assert_called_once_with(
        node_label="Image", edge_label="HAS_IMAGE"
    )


def test_get_edge_ids_to_remove_yields_nothing() -> None:
    remover, _ = _make_remover()

    assert list(remover.get_edge_ids_to_remove()) == []
