from typing import Literal

from models.graph_edge import BaseEdge
from models.graph_node import BaseNode

from .base_converter import CypherBaseConverter


class CypherQueryConverter(CypherBaseConverter):
    def __init__(self, entity_type: Literal["nodes", "edges"]):
        self.entity_type = entity_type

    def _convert_str(self, raw_value: str) -> str:
        # All strings need to be surrounded in single quotation marks, and all single quotation marks
        # which were already present in the string need to be escaped.
        escaped = raw_value.replace("'", "\\'")
        return f"'{escaped}'"

    def _node_to_cypher_map(self, model: BaseNode) -> str:
        properties = []

        for key, raw_value in model.dict().items():
            value = self._raw_value_to_cypher_value(raw_value)
            properties.append(f"{key}: {value}")

        return "{" + ", ".join(properties) + "}"

    def _edge_to_cypher_map(self, model: BaseEdge) -> str:
        properties = []

        for key, raw_value in model.attributes.items():
            value = self._raw_value_to_cypher_value(raw_value)
            properties.append(f"{key}: {value}")

        for key, raw_value in model:
            if key in ("from_id", "to_id"):
                value = self._raw_value_to_cypher_value(raw_value)
                properties.append(f"{key}: {value}")

        return "{" + ", ".join(properties) + "}"

    def convert_to_cypher_map(self, model: BaseNode | BaseEdge) -> str:
        """
        Returns a string representing an openCypher Map of the entity (node or edge) for use with an `UNWIND` query.

        For example, the Pydantic model `BaseNode(id="someId123", label="Some Label")` would be converted into
        the string `{id: 'someId123', label: 'Some Label'}`.

        See https://neo4j.com/docs/cypher-manual/current/values-and-types/maps/.
        """
        if self.entity_type == "nodes":
            return self._node_to_cypher_map(model)
        elif self.entity_type == "edges":
            return self._edge_to_cypher_map(model)
        else:
            raise ValueError(
                "Unsupported Pydantic model. Each model must subclass BaseEdge or BaseNode."
            )
