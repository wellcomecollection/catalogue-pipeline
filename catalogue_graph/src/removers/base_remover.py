from collections.abc import Iterable, Iterator
from itertools import batched

from utils.aws import get_neptune_client
from utils.types import EntityType

ES_QUERY_NON_VISIBLE_WORKS = {"bool": {"must_not": {"match": {"type": "Visible"}}}}


class BaseGraphRemover:
    def __init__(self, entity_type: EntityType, use_public_endpoint: bool):
        self.neptune_client = get_neptune_client(use_public_endpoint)
        self.entity_type = entity_type

    def get_node_ids_to_remove(self) -> Iterator[str]:
        """Return an iterator of node IDs which should be removed from the catalogue graph."""
        raise NotImplementedError()

    def get_es_edges(self) -> Iterator[tuple[str, set[str]]]:
        """
        Return an iterator in the format `("start_id", {"end_id_1", "end_id_2", ...})`, where `start_id`
        is the ID of some node in Neptune,
        """
        raise NotImplementedError()

    def get_graph_edges(self, start_ids: Iterable[str]) -> dict[str, set[str]]:
        raise NotImplementedError()

    def get_edge_ids_to_remove(self) -> Iterator[str]:
        for batch in batched(self.get_es_edges(), 40_000):
            es_edges = {work_id: linked_ids for work_id, linked_ids in batch}
            graph_edges = self.get_graph_edges(es_edges.keys())

            for work_id, graph_edge_ids in graph_edges.items():
                yield from graph_edge_ids.difference(es_edges.get(work_id, set()))

    def remove(self) -> list[str]:
        if self.entity_type == "nodes":
            ids_to_remove = self.get_node_ids_to_remove()
        elif self.entity_type == "edges":
            ids_to_remove = self.get_edge_ids_to_remove()
        else:
            raise ValueError(f"Unknown entity type: {self.entity_type}")

        deleted_ids = []
        for batch in batched(ids_to_remove, 40_000):
            print(f"Will delete a batch of up to {len(batch)} IDs from the graph.")
            deleted_ids += self.neptune_client.delete_entities_by_id(
                list(batch), self.entity_type
            )

        print(
            f"Deleted a total of {len(deleted_ids)} {self.entity_type} from the graph."
        )

        return deleted_ids
