from typing import Literal

from models.graph_edge import BaseEdge
from models.graph_node import BaseNode

from .base_converter import CypherBaseConverter


class CypherBulkLoadConverter(CypherBaseConverter):
    def __init__(self, entity_type: Literal["nodes", "edges"]):
        self.entity_type = entity_type

    def _node_to_bulk_cypher(self, model: BaseNode):
        bulk_node = {":ID": model.id, ":LABEL": type(model).__name__}

        for key, raw_value in model.dict().items():
            value = self._raw_value_to_cypher_value(raw_value)
            bulk_node[key] = value

        return bulk_node

    def _edge_to_bulk_cypher(self, model: BaseEdge):
        bulk_edge = {
            ":ID": f"{model.from_id}-->{model.to_id}",
            ":START_ID": model.from_id,
            ":END_ID": model.to_id,
            ":TYPE": model.relationship,
        }

        for key, raw_value in model.attributes.items():
            value = self._raw_value_to_cypher_value(raw_value)
            bulk_edge[key] = value

        return bulk_edge

    def convert_to_bulk_cypher(self, model: BaseNode | BaseEdge):
        """
        Returns a dictionary representing the entity (node or edge), converting all values into a format compatible
        with openCypher, and adding all required values for bulk upload, such as `:ID` or `:LABEL`.
        See https://docs.aws.amazon.com/neptune/latest/userguide/bulk-load-tutorial-format-opencypher.html.
        """
        if self.entity_type == "nodes":
            return self._node_to_bulk_cypher(model)
        elif self.entity_type == "edges":
            return self._edge_to_bulk_cypher(model)
        else:
            raise ValueError(
                "Unsupported Pydantic model. Each model must subclass BaseEdge or BaseNode."
            )
