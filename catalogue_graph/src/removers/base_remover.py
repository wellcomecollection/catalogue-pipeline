from itertools import batched

from utils.aws import get_neptune_client
from utils.types import EntityType

ES_QUERY_NON_VISIBLE_WORKS = {"bool": {"must_not": {"match": {"type": "Visible"}}}}


class BaseGraphRemover:
    def __init__(self, entity_type: EntityType, use_public_endpoint: bool):
        self.neptune_client = get_neptune_client(use_public_endpoint)
        self.entity_type = entity_type

    def get_node_ids_to_remove(self):
        raise NotImplementedError()

    def get_edge_ids_to_remove(self):
        raise NotImplementedError()

    def remove(self) -> list[str]:
        if self.entity_type == "nodes":
            ids_to_remove = self.get_node_ids_to_remove()
        elif self.entity_type == "edges":
            ids_to_remove = self.get_edge_ids_to_remove()
        else:
            raise ValueError(f"Unknown entity type: {self.entity_type}")

        deleted_ids = []
        for batch in batched(ids_to_remove, 40_000):
            print(
                f"Will delete a batch of {len(batch)} up to IDs from the catalogue graph."
            )
            deleted_ids = self.neptune_client.delete_entities_by_id(
                list(batch), self.entity_type
            )

        print(
            f"Deleted a total of {len(deleted_ids)} {self.entity_type} from the graph."
        )

        return deleted_ids
