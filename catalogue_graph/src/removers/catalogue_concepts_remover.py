from collections import defaultdict
from collections.abc import Iterable, Iterator
from itertools import batched

from converters.cypher.bulk_load_converter import get_graph_edge_id
from ingestor.extractors.concepts_extractor import ES_FIELDS
from models.graph_edge import WorkHasConcept
from removers.base_remover import BaseGraphRemover
from sources.catalogue.concepts_source import extract_concepts_from_work
from sources.merged_works_source import MergedWorksSource
from utils.elasticsearch import ElasticsearchMode
from utils.streaming import process_stream_in_parallel

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

    def _get_es_work_concepts(self, es_works: Iterable[dict]) -> dict[str, set[str]]:
        work_concept_map = defaultdict(set)

        for work in es_works:
            work_id = work["state"]["canonicalId"]
            for concept, _ in extract_concepts_from_work(work):
                try:
                    work_concept_map[work_id].add(concept["id"]["canonicalId"])
                except KeyError:
                    print(f"Concept {concept} does not have an ID.")

        return work_concept_map

    def _get_graph_work_concepts(self, work_ids: Iterable[str]) -> dict[str, set[str]]:
        def run_get_query(batch: Iterator[str]) -> list[str]:
            return self.neptune_client.run_open_cypher_query(
                query, {"ids": list(batch)}
            )

        query = """
            UNWIND $ids AS id
            MATCH (w: Work {`~id`: id})-[:HAS_CONCEPT]->(c)
            RETURN id(w) AS id, collect(id(c)) AS concept_ids
        """

        result = list(process_stream_in_parallel(work_ids, run_get_query, 2000, 5))

        work_concept_map = {}
        for item in result:
            work_concept_map[item["id"]] = set(item["concept_ids"])

        return work_concept_map

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

    def get_edge_ids_to_remove(self) -> Iterator[str]:
        for batch in batched(self.es_source.stream_raw(), 40_000):
            es_work_concepts = self._get_es_work_concepts(batch)
            work_ids = es_work_concepts.keys()
            graph_work_concepts = self._get_graph_work_concepts(work_ids)

            for work_id, concept_ids in graph_work_concepts.items():
                for concept_id in concept_ids.difference(es_work_concepts[work_id]):
                    edge = WorkHasConcept(from_id=work_id, to_id=concept_id)
                    print(get_graph_edge_id(edge))
                    yield get_graph_edge_id(edge)
