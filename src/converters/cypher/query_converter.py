from models.graph_node import BaseNode
from models.graph_edge import BaseEdge
from .base_converter import CypherBaseConverter


class CypherQueryConverter(CypherBaseConverter):
    def __init__(self, model_to_convert: BaseEdge | BaseNode):
        self.model = model_to_convert

    def _convert_str(self, raw_value: str) -> str:
        # All strings need to be surrounded in single quotation marks, and all single quotation marks
        # which were already present in the string need to be escaped.
        escaped = raw_value.replace("'", "\\'")
        return f"'{escaped}'"

    def _node_to_cypher_map(self) -> str:
        properties = []

        for key, raw_value in dict(self.model).items():
            value = self._raw_value_to_cypher_value(raw_value)
            properties.append(f"{key}: {value}")

        return "{" + ", ".join(properties) + "}"

    def _edge_to_cypher_map(self) -> str:
        properties = []

        for key, raw_value in self.model.attributes.items():
            value = self._raw_value_to_cypher_value(raw_value)
            properties.append(f"{key}: {value}")

        for key, raw_value in self.model:
            if key in ("from_id", "to_id"):
                value = self._raw_value_to_cypher_value(raw_value)
                properties.append(f"{key}: {value}")

        return "{" + ", ".join(properties) + "}"

    def convert_to_cypher_map(self):
        """
        Returns a string representing an openCypher Map of the entity (node or edge) for use with an `UNWIND` query.

        For example, the Pydantic model `BaseNode(id="someId123", label="Some Label")` would be converted into
        the string `{id: 'someId123', label: 'Some Label'}`.

        See https://neo4j.com/docs/cypher-manual/current/values-and-types/maps/.
        """
        if isinstance(self.model, BaseNode):
            return self._node_to_cypher_map()
        elif isinstance(self.model, BaseEdge):
            return self._edge_to_cypher_map()
        else:
            raise ValueError(
                "Unsupported Pydantic model. Each model must subclass BaseEdge or BaseNode."
            )
