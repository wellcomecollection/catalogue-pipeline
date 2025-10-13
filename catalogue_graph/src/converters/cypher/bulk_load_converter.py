from typing import Literal, cast

from pydantic import BaseModel

from models.graph_edge import BaseEdge, get_all_edge_attributes
from models.graph_node import BaseNode, SourceLocation, SourceName

from .base_converter import CypherBaseConverter


def get_graph_edge_id(model: BaseEdge) -> str:
    return f"{model.relationship}:{model.from_id}-->{model.to_id}"


class CypherBulkLoadConverter(CypherBaseConverter):
    def __init__(self, entity_type: Literal["nodes", "edges"]):
        self.entity_type = entity_type

    def _get_bulk_loader_column_header(self, model: BaseModel, field_name: str) -> str:
        """
        Return a Neptune bulk loader column header, defining the name and type of the column. See here for more info:
        https://docs.aws.amazon.com/neptune/latest/userguide/bulk-load-tutorial-format-opencypher.html#bulk-load-tutorial-format-opencypher-data-types
        """
        # Most fields are stored as strings
        field_type = "String"
        if isinstance(model, SourceLocation) and field_name in {
            "longitude",
            "latitude",
        }:
            field_type = "Float"
        if isinstance(model, SourceName) and field_name in {
            "date_of_birth",
            "date_of_death",
        }:
            field_type = "DateTime"

        return f"{field_name}:{field_type}"

    def _node_to_bulk_cypher(self, model: BaseNode) -> dict:
        bulk_node = {":ID": model.id, ":LABEL": type(model).__name__}

        for field_name, raw_value in model.dict().items():
            column_header = self._get_bulk_loader_column_header(model, field_name)
            value = self._raw_value_to_cypher_value(raw_value)
            bulk_node[column_header] = value

        return bulk_node

    def _edge_to_bulk_cypher(self, model: BaseEdge) -> dict:
        bulk_edge = {
            # We need to give the edge a unique ID so that the Neptune bulk loader recognises duplicates
            ":ID": get_graph_edge_id(model),
            ":START_ID": model.from_id,
            ":END_ID": model.to_id,
            ":TYPE": model.relationship,
        }

        for edge_attribute_name in get_all_edge_attributes():
            column_header = self._get_bulk_loader_column_header(
                model, edge_attribute_name
            )
            raw_value = model.attributes.dict().get(edge_attribute_name, None)
            value = self._raw_value_to_cypher_value(raw_value)
            bulk_edge[column_header] = value

        return bulk_edge

    def convert_to_bulk_cypher(self, model: BaseNode | BaseEdge) -> dict[str, str]:
        """
        Returns a dictionary representing the entity (node or edge), converting all values into a format compatible
        with openCypher, and adding all required values for bulk upload, such as `:ID` or `:LABEL`.
        See https://docs.aws.amazon.com/neptune/latest/userguide/bulk-load-tutorial-format-opencypher.html.
        """
        if self.entity_type == "nodes":
            return self._node_to_bulk_cypher(cast(BaseNode, model))
        elif self.entity_type == "edges":
            return self._edge_to_bulk_cypher(cast(BaseEdge, model))
        else:
            raise ValueError(
                "Unsupported Pydantic model. Each model must subclass BaseEdge or BaseNode."
            )
