from collections.abc import Iterator

from models.events import IncrementalGraphRemoverEvent
from utils.elasticsearch import ElasticsearchMode

from .base_remover import BaseGraphRemover


class CatalogueConceptsGraphRemover(BaseGraphRemover):
    def __init__(self, event: IncrementalGraphRemoverEvent, es_mode: ElasticsearchMode):
        super().__init__(event.entity_type, es_mode != "private")

    def get_node_ids_to_remove(self) -> Iterator[str]:
        """Remove the IDs of all concept nodes which are not connected to any works"""
        yield from self.neptune_client.get_disconnected_node_ids(
            node_label="Concept", edge_label="HAS_CONCEPT"
        )

    def get_edge_ids_to_remove(self) -> Iterator[str]:
        # At the moment, we don't remove any concept edges using the incremental remover
        yield from ()
