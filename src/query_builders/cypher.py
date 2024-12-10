from pydantic import BaseModel

from models.graph_edge import BaseEdge
from models.graph_node import BaseNode


def _value_to_cypher_value(raw_value: any):
    if isinstance(raw_value, str):
        escaped = raw_value.replace("'", "\\'")
        value = f"'{escaped}'"
    elif isinstance(raw_value, bool):
        value = str(raw_value).lower()
    elif isinstance(raw_value, list):
        # Neptune does not support lists, so we convert them to a single string with a `||` separator
        value = _value_to_cypher_value("||".join(raw_value))
    elif raw_value is None:
        value = "null"
    else:
        raise TypeError(
            f"""
                Cannot convert type {type(raw_value)} (with value {repr(raw_value)}) into a Cypher data type.
                Use a different type or add support for type {type(raw_value)} to CypherClient.
                """
        )

    return value


def _pydantic_object_to_cypher_map(pydantic_object: BaseModel):
    properties = []

    for key, raw_value in dict(pydantic_object).items():
        value = _value_to_cypher_value(raw_value)
        properties.append(f"{key}: {value}")

    return "{" + ", ".join(properties) + "}"


def construct_upsert_nodes_query(source_concepts: list[BaseNode]):
    model_name = type(source_concepts[0]).__name__
    all_fields = type(source_concepts[0]).model_fields.keys()

    field_set = [f"n.{f} = data.{f}" for f in all_fields]
    field_set_statement = ", ".join(field_set)

    cypher_maps = [
        _pydantic_object_to_cypher_map(concept) for concept in source_concepts
    ]
    joined_cypher_maps = ",\n".join(cypher_maps)

    query = f"""
            UNWIND [
                {joined_cypher_maps}
            ] AS data
            MERGE (n:{model_name} {{id: data.id}})
            ON CREATE SET {field_set_statement}
            ON MATCH SET {field_set_statement}
        """
    return query


def construct_upsert_edges_query(edges: list[BaseEdge]):
    from_type = edges[0].from_type
    to_type = edges[0].to_type
    relationship = edges[0].relationship

    joined_cypher_maps = ",\n".join(
        [_pydantic_object_to_cypher_map(edge) for edge in edges]
    )
    query = f"""
            UNWIND [
                {joined_cypher_maps}
            ] AS data
            MATCH (a:{from_type} {{id: data.from_id}})
            MATCH (b:{to_type} {{id: data.to_id}})
            MERGE (a)-[r:{relationship}]->(b)
        """
    return query
