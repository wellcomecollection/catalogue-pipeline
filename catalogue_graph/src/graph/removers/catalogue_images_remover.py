from collections.abc import Iterator

from clients.neptune_client import NeptuneClient
from models.events import IncrementalGraphRemoverEvent

from .base_graph_remover_incremental import BaseGraphRemoverIncremental


class CatalogueImagesGraphRemover(BaseGraphRemoverIncremental):
    def __init__(
        self, event: IncrementalGraphRemoverEvent, neptune_client: NeptuneClient
    ):
        super().__init__(event.entity_type, neptune_client)

    def get_total_node_count(self) -> int:
        return self.neptune_client.get_total_node_count("Image")

    def get_total_edge_count(self) -> int:
        return 0

    def get_node_ids_to_remove(self) -> Iterator[str]:
        """Remove the IDs of all image nodes which are not connected to any works"""
        yield from self.neptune_client.get_disconnected_node_ids(
            node_label="Image", edge_label="HAS_IMAGE"
        )

    def get_edge_ids_to_remove(self) -> Iterator[str]:
        # At the moment, we don't remove any image edges using the incremental remover
        yield from ()
