from typing import Literal, cast

from converters.cypher.query_converter import CypherQueryConverter
from models.graph_edge import BaseEdge
from models.graph_node import BaseNode


def construct_upsert_nodes_query(nodes: list[BaseNode]) -> str:
    model_name = type(nodes[0]).__name__
    all_fields = type(nodes[0]).model_fields.keys()

    field_set = [f"n.{f} = data.{f}" for f in all_fields]
    field_set_statement = ", ".join(field_set)

    converter = CypherQueryConverter("nodes")
    unwind_maps = [converter.convert_to_cypher_map(node) for node in nodes]
    joined_unwind_maps = ",\n".join(unwind_maps)

    query = f"""
            UNWIND [
                {joined_unwind_maps}
            ] AS data
            MERGE (n:{model_name} {{id: data.id}})
            ON CREATE SET {field_set_statement}
            ON MATCH SET {field_set_statement}
        """
    return query


def construct_upsert_edges_query(edges: list[BaseEdge]) -> str:
    from_type = edges[0].from_type
    to_type = edges[0].to_type
    relationship = edges[0].relationship
    attributes = edges[0].attributes.dict()

    field_set = [f"n.{f} = data.{f}" for f in attributes]
    field_set_statement = ", ".join(field_set)

    if len(field_set_statement) == 0:
        field_set_statement = "r={}"

    converter = CypherQueryConverter("edges")
    unwind_maps = [converter.convert_to_cypher_map(edge) for edge in edges]
    joined_unwind_maps = ",\n".join(unwind_maps)

    query = f"""
            UNWIND [
                {joined_unwind_maps}
            ] AS data
            MATCH (a:{from_type} {{id: data.from_id}})
            MATCH (b:{to_type} {{id: data.to_id}})
            MERGE (a)-[r:{relationship}]->(b)
            ON CREATE SET {field_set_statement}
            ON MATCH SET {field_set_statement}
        """
    return query


def construct_upsert_cypher_query(
    entities: list[BaseNode | BaseEdge], entity_type: Literal["nodes", "edges"]
) -> str:
    """
    Returns an openCypher `UNWIND` query which creates a graph node or edge for each item specified in `entities`,
    or updates an existing matching node or edge.

    All passed `entities` must be instances of the same Pydantic model because labels cannot be set dynamically
    in openCypher.
    """
    if entity_type == "nodes":
        return construct_upsert_nodes_query(cast(list[BaseNode], entities))
    elif entity_type == "edges":
        return construct_upsert_edges_query(cast(list[BaseEdge], entities))
    else:
        raise ValueError(
            "Unsupported Pydantic model. Each model must subclass BaseEdge or BaseNode."
        )
