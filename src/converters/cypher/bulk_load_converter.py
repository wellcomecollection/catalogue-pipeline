from models.graph_node import BaseNode
from models.graph_edge import BaseEdge
from .base_converter import CypherBaseConverter


class CypherBulkLoadConverter(CypherBaseConverter):
    def __init__(self, model_to_convert: BaseEdge | BaseNode):
        self.model = model_to_convert

    def _node_to_bulk_cypher(self):
        bulk_node = {":ID": self.model.id, ":LABEL": type(self.model).__name__}

        for key, raw_value in dict(self.model).items():
            value = self._raw_value_to_cypher_value(raw_value)
            bulk_node[key] = value

        return bulk_node

    def _edge_to_bulk_cypher(self):
        bulk_edge = {
            ":ID": f"{self.model.from_id}-->{self.model.to_id}",
            ":START_ID": self.model.from_id,
            ":END_ID": self.model.to_id,
            ":TYPE": self.model.relationship,
        }

        for key, raw_value in self.model.attributes.items():
            value = self._raw_value_to_cypher_value(raw_value)
            bulk_edge[key] = value

        return bulk_edge

    def convert_to_bulk_cypher(self):
        """
        Returns a dictionary representing the entity (node or edge), converting all values into a format compatible
        with openCypher, and adding all required values for bulk upload, such as `:ID` or `:LABEL`.
        See https://docs.aws.amazon.com/neptune/latest/userguide/bulk-load-tutorial-format-opencypher.html.
        """
        if isinstance(self.model, BaseNode):
            return self._node_to_bulk_cypher()
        elif isinstance(self.model, BaseEdge):
            return self._edge_to_bulk_cypher()
        else:
            raise ValueError(
                "Unsupported Pydantic model. Each model must subclass BaseEdge or BaseNode."
            )
