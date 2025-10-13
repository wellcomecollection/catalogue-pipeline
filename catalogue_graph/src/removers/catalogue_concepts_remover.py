from collections.abc import Iterator

from ingestor.extractors.concepts_extractor import ES_FIELDS
from sources.merged_works_source import MergedWorksSource
from utils.elasticsearch import ElasticsearchMode

from .base_remover import BaseGraphRemover

ES_QUERY = {"match": {"type": "Visible"}}


class CatalogueConceptsGraphRemover(BaseGraphRemover):
    def __init__(self, event, es_mode: ElasticsearchMode):
        super().__init__(event.entity_type, es_mode != "private")
        self.es_source = MergedWorksSource(
            event,
            query=ES_QUERY,
            fields=ES_FIELDS,
            es_mode=es_mode,
        )

    def get_node_ids_to_remove(self) -> Iterator[str]:
        """Remove the IDs of all concept nodes which are not connected to any works"""
        query = """
            MATCH (c: Concept)
            WHERE NOT (c)<-[:HAS_CONCEPT]->()
            RETURN id(c) AS id
        """

        result = self.neptune_client.time_open_cypher_query(
            query, {}, "unused concepts"
        )

        for item in result:
            yield item["id"]

    def get_work_concepts(self, work_ids: list[str]) -> Iterator[str]:
        query = """
            UNWIND $ids AS id
            MATCH (w: Work {`~id`: id})-[:HAS_CONCEPT]->(c)
            RETURN id(w) AS id, collect(id(c)) AS concept_ids
        """

        result = self.neptune_client.time_open_cypher_query(
            query, {"ids": work_ids}, "work concepts"
        )

        for item in result:
            yield item["id"]

    def get_edge_ids_to_remove(self) -> Iterator[str]:
        for work in self.es_source.stream_raw():
            print(work)

        yield from ()
