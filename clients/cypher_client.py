from pydantic import BaseModel

from .neptune_client import NeptuneClient
from models.graph_node import SourceConcept
from models.graph_edge import BaseEdge


class CypherClient:
    def __init__(self, neptune_client: NeptuneClient):
        self.neptune_client = neptune_client

    def _value_to_cypher_value(self, raw_value: any):
        if isinstance(raw_value, str):
            escaped = raw_value.replace("'", "\\'")
            value = f"'{escaped}'"
        elif isinstance(raw_value, bool):
            value = str(raw_value).lower()
        elif isinstance(raw_value, list):
            # if len(raw_value) == 0:
            #     value = "null"
            # else:
            # Neptune does not support lists, so we convert them to a single string with a `||` separator
            value = self._value_to_cypher_value("||".join(raw_value))
        elif raw_value is None:
            value = "null"
        else:
            raise TypeError(f"""Cannot convert type {type(raw_value)} (with value {repr(raw_value)}) into a Cypher data
                            type. Use a different type or add support for type {type(raw_value)} to CypherClient.""")

        return value

    def _pydantic_object_to_cypher_map(self, pydantic_object: BaseModel):
        properties = []

        for key, raw_value in pydantic_object.items():
            value = self._value_to_cypher_value(raw_value)
            properties.append(f"{key}: {value}")

        return "{" + ", ".join(properties) + "}"

    def create_source_concept_nodes(self, source_concepts: list[SourceConcept]):
        all_fields = SourceConcept.__fields__.keys()
        field_set = [f"n.{f} = data.{f}" for f in all_fields]
        field_set_statement = ", ".join(field_set)

        cypher_maps = [self._pydantic_object_to_cypher_map(concept) for concept in source_concepts]
        joined_cypher_maps = ",\n".join(cypher_maps)

        query = f"""
            UNWIND [
                {joined_cypher_maps}
            ] AS data
            MERGE (n:SourceConcept {{source_id: data.source_id}})
            ON CREATE SET {field_set_statement}
            ON MATCH SET {field_set_statement}
        """

        return self.neptune_client.run_open_cypher_query(query)

    def upsert_edges(self, edges: list[BaseEdge]):
        from_type = edges[0].from_type
        to_type = edges[0].to_type
        relationship = edges[0].relationship

        joined_cypher_maps = ",\n".join([self._pydantic_object_to_cypher_map(edge) for edge in edges])
        query = f"""
            UNWIND [
                {joined_cypher_maps}
            ] AS data
            MATCH (a:{from_type} {{source_id: data.from_id}})
            MATCH (b:{to_type} {{source_id: data.to_id}})
            MERGE (a)-[r:{relationship}]->(b)
        """

        return self.neptune_client.run_open_cypher_query(query)
