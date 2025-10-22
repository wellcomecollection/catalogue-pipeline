from collections.abc import Iterable, Iterator
from itertools import batched

from utils.aws import get_neptune_client
from utils.safety import validate_fractional_change
from utils.types import EntityType

ES_QUERY_NON_VISIBLE_WORKS = {"bool": {"must_not": {"match": {"type": "Visible"}}}}

BATCH_SIZE = 40_000


class BaseGraphNodeRemover:
    def get_total_node_count(self) -> int:
        raise NotImplementedError()

    def get_node_ids_to_remove(self) -> Iterator[str]:
        """Return an iterator of node IDs which should be removed from the catalogue graph."""
        raise NotImplementedError()


class BaseGraphEdgeRemover:
    def get_total_edge_count(self) -> int:
        raise NotImplementedError()

    def get_es_edges(self) -> Iterator[tuple[str, set[str]]]:
        """
        Return an iterator in the format `("node_id", {"edge_id_1", "edge_id_2", ...})`, mapping each node ID
        to a set of edge IDs extracted from the merged index.
        """
        raise NotImplementedError()

    def get_graph_edges(self, node_ids: Iterable[str]) -> dict[str, set[str]]:
        """
        Return a dictionary in the format `{"node_id": {"edge_id_1", "edge_id_2", ...}}`, mapping each node ID
        to a set of existing edge IDs based on data from the catalogue graph.
        """
        raise NotImplementedError()

    def get_edge_ids_to_remove(self) -> Iterator[str]:
        """
        Compare the output of `get_es_edges` against the output of `get_graph_edges`. Remove edges which only exist
        in the latter and not in the former.
        """
        for batch in batched(self.get_es_edges(), BATCH_SIZE):
            es_edges = {work_id: linked_ids for work_id, linked_ids in batch}
            graph_edges = self.get_graph_edges(es_edges.keys())

            # Remove all edges which exist in the graph but not in the merged index.
            for work_id, graph_edge_ids in graph_edges.items():
                yield from graph_edge_ids.difference(es_edges.get(work_id, set()))


class BaseGraphRemoverIncremental(BaseGraphEdgeRemover, BaseGraphNodeRemover):
    def __init__(self, entity_type: EntityType, use_public_endpoint: bool):
        self.neptune_client = get_neptune_client(use_public_endpoint)
        self.entity_type = entity_type

    def remove(self, override_safety_check: bool = False) -> list[str]:
        if self.entity_type == "nodes":
            total_count = self.get_total_node_count()
            ids_to_remove = self.get_node_ids_to_remove()
        elif self.entity_type == "edges":
            total_count = self.get_total_edge_count()
            ids_to_remove = self.get_edge_ids_to_remove()
        else:
            raise ValueError(f"Unknown entity type: {self.entity_type}")

        existing_ids = []
        for batch in batched(ids_to_remove, BATCH_SIZE):
            # Filter for IDs which actually exist in the graph
            existing_ids += self.neptune_client.get_existing_ids(
                list(batch), self.entity_type
            )

        print(
            f"Will delete a total of {len(existing_ids)} {self.entity_type} from the graph."
        )

        # This is part of a safety mechanism. If the fraction of removed nodes/edges of the given type exceeds
        # `DEFAULT_THRESHOLD` (set to 5%), an exception will be raised.
        validate_fractional_change(
            modified_size=len(existing_ids),
            total_size=total_count,
            force_pass=override_safety_check,
        )

        self.neptune_client.delete_entities_by_id(existing_ids, self.entity_type)

        print(f"Deleted {len(existing_ids)} {self.entity_type} from the graph.")

        return existing_ids
