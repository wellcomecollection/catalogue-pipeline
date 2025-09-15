import time
from collections import defaultdict
from collections.abc import Generator
from concurrent.futures import ThreadPoolExecutor
from itertools import batched
from typing import Iterable, Literal, get_args

from models.events import IncrementalWindow
from sources.catalogue.concepts_source import extract_concepts_from_work
from sources.threaded_es_source import ThreadedElasticsearchSource
from utils.aws import get_neptune_client
from utils.streaming import process_stream_in_parallel

from ingestor.models.neptune.query_result import NeptuneConcept
from ingestor.queries.concept_queries import (
    CONCEPT_QUERY,
    CONCEPT_TYPE_QUERY,
    SAME_AS_CONCEPT_QUERY,
    SOURCE_CONCEPT_QUERY,
    get_referenced_together_query,
    get_related_query,
)

NEPTUNE_PARAMS = {
    # There are a few Wikidata supernodes which cause performance issues in queries.
    # We need to filter them out when running queries to get related nodes.
    # Q5 -> 'human', Q151885 -> 'concept'
    "ignored_wikidata_ids": ["Q5", "Q151885"],
    # Maximum number of related nodes to return for each relationship type
    "related_to_limit": 10,
    # Minimum number of works in which two concepts must co-occur to be considered 'frequently referenced together'
    "shared_works_count_threshold": 3,
}

ES_QUERY = {"match": {"type": "Visible"}}
ES_FIELDS = [
    "data.subjects",
    "data.contributors",
    "data.genres",
]

RelatedQuery = Literal["related_to", "fields_of_work", "narrower_than", "broader_than", "people"]
ReferencedTogetherQuery = Literal["frequent_collaborators", "related_topics"]
ConceptQuery = Literal["concept", "concept_type", "source_concept", "same_as_concept", RelatedQuery, ReferencedTogetherQuery]

NEPTUNE_QUERIES = {
    "concept": CONCEPT_QUERY,
    "concept_type": CONCEPT_TYPE_QUERY,
    "source_concept": SOURCE_CONCEPT_QUERY,
    "same_as_concept": SAME_AS_CONCEPT_QUERY, 
    "related_to": get_related_query("RELATED_TO"),
    "fields_of_work": get_related_query("HAS_FIELD_OF_WORK"),
    "narrower_than": get_related_query("NARROWER_THAN"),
    "broader_than": get_related_query("NARROWER_THAN|HAS_PARENT", "to"),
    "people": get_related_query("HAS_FIELD_OF_WORK", "to"),
    # Retrieve people and orgs which are commonly referenced together as collaborators with a given person/org
    "frequent_collaborators": get_referenced_together_query(
        source_referenced_types=["Person", "Organisation"],
        related_referenced_types=["Person", "Organisation"],
        source_referenced_in=["contributors"],
        related_referenced_in=["contributors"],
    ),
    # Do not include agents/people/orgs in the list of related topics.
    "related_topics": get_referenced_together_query(
        related_referenced_types=[
            "Concept",
            "Subject",
            "Place",
            "Meeting",
            "Period",
            "Genre",
        ],
        related_referenced_in=["subjects"],
    )
}

LinkedConcepts = dict[str, list[dict]]


class GraphConceptsExtractor:
    def __init__(self, pipeline_date: str, window: IncrementalWindow = None, is_local: bool = False):
        self.es_source = ThreadedElasticsearchSource(pipeline_date=pipeline_date, query=ES_QUERY, fields=ES_FIELDS, window=window, is_local=is_local)
        self.neptune_client = get_neptune_client(is_local)
            
        self.redirect_map = {}
        self.same_as_map = {}

    def make_neptune_query(self, query_type: ConceptQuery, ids: Iterable[str]) -> dict:
        chunk_size = 1000 if query_type in {"related_topics", "frequent_collaborators"} else 5000
        query_count = 0
    
        def _make_query(chunk: Iterable[str]):
            nonlocal query_count
            query_count += 1
            return self.neptune_client.run_open_cypher_query(
                NEPTUNE_QUERIES[query_type], NEPTUNE_PARAMS | {"ids": chunk}
            )
    
        start = time.time()
        raw_result = process_stream_in_parallel(ids, _make_query, chunk_size, 5)
        results = {item["id"]: item for item in raw_result}
    
        print(
            f"Ran {query_count} '{query_type}' queries in {round(time.time() - start)}s, "
            f"retrieving {len(results)} records."
        )
        return results
    
    def same_as(self, concept_id: str):
        primary_id = self.redirect_map[concept_id]
        return self.same_as_map[primary_id]
    
    def get_concept_types(self, ids: Iterable[str]):        
        all_ids = set(ids)
        for i in ids:
            all_ids.update(self.same_as(i))

        types_result = self.make_neptune_query('concept_type', all_ids)
        
        concept_types = defaultdict(lambda: {"Concept"})
        for concept_id in ids:
            for same_as_concept_id in self.same_as(concept_id):
                if same_as_concept_id in types_result:
                    concept_types[concept_id].update(types_result[same_as_concept_id]['types'])
        
        return concept_types

    def get_concepts(self, ids: Iterable[str]):
        concepts = self.make_neptune_query("concept", ids)
        source_concepts = self.make_neptune_query("source_concept", ids)
        concept_types = self.get_concept_types(ids)
        
        full_concepts = {}
        for concept_id in concepts:
            source = source_concepts.get(concept_id, {})
            
            neptune_concept = NeptuneConcept(
                concept=concepts[concept_id]["concept"],
                types=concept_types["concept_id"],
                source_concepts=source.get("source_concepts", []),
                same_as=self.same_as(concept_id),
                linked_source_concept=source.get("linked_source_concepts", [None])[0]
            )
            full_concepts[concept_id] = neptune_concept
        
        return full_concepts

    def get_same_as_redirect(self, ids):
        to_process = set()
        for i in ids:
            if i not in self.redirect_map:
                to_process.add(i)

        result = self.make_neptune_query("same_as_concept", to_process)
        
        for i in to_process:
            if i not in self.redirect_map:
                self.redirect_map[i] = i
            if i not in self.same_as_map:
                self.same_as_map[i] = [i]

        for concept_id, item in result.items():
            same_as_ids = [concept_id] + item['same_as_ids']

            # Alphabetical ID-based prioritisation
            primary_id = sorted(same_as_ids)[0]

            self.same_as_map[primary_id] = same_as_ids
            for same_as_id in same_as_ids:
                self.redirect_map[same_as_id] = primary_id
                
        return result
    
    def get_related_concepts(self, query_type: ConceptQuery, ids: Iterable[str]) -> LinkedConcepts:
        result = self.make_neptune_query(query_type, ids)
        
        related_ids = set()
        for item in result.values():
            related_ids.update([i["id"] for i in item["related"]])

        self.get_same_as_redirect(related_ids)
        
        full_related_concepts = self.get_concepts(related_ids)

        full_result = defaultdict(list)
    
        for concept_id in ids:
            for item in result.get(concept_id, {}).get("related", []):
                full_result[concept_id].append(full_related_concepts[item["id"]])
        
        return full_result

    def get_referenced_together_concepts(self, query_type: ConceptQuery, ids: Iterable[str]):
        result = self.make_neptune_query(query_type, ids)
        
        related_ids = set()
        for item in result.values():
            for related in item['related']:
                related_ids.add(related['id'])
        
        self.get_same_as_redirect(related_ids)
        
        # Merge results across *same as* concepts and related concepts
        merged_frequent_collaborators = defaultdict(lambda: defaultdict(lambda: 0))
        for concept_id, item in result.items():
            primary_id = self.redirect_map[concept_id]
        
            for related in item["related"]:
                primary_related_id = self.redirect_map[related['id']]
        
                # Make sure a concept does not reference itself
                if self.redirect_map[primary_id] != self.redirect_map[primary_related_id]:
                    merged_frequent_collaborators[primary_id][primary_related_id] += related['count']
        
        sorted_frequent_collaborators = {}
        for concept_id, collaborators in merged_frequent_collaborators.items():
            # Get first 10 results
            sorted_items = sorted(collaborators.items(), key=lambda i: -i[1])[:10]
            sorted_frequent_collaborators[concept_id] = [i[0] for i in sorted_items]
        
        related_concept_ids = set()
        for related_ids in merged_frequent_collaborators.values():
            related_concept_ids.update(related_ids)
        
        full_related_concepts = self.get_concepts(related_concept_ids)
        
        full_frequent_collaborators = defaultdict(list)
        
        for concept_id in ids:
            for related_id in sorted_frequent_collaborators.get(concept_id, []):
                full_frequent_collaborators[concept_id].append(full_related_concepts[related_id])
        
        return full_frequent_collaborators        

    def concept_ids_from_works(self) -> Generator[str]:
        for work in self.es_source.stream_raw():
            for concept, _ in extract_concepts_from_work(work["data"]): 
                try:
                    yield concept["id"]["canonicalId"]
                except:
                    print(concept, work)
    
    def same_as_concepts(self) -> Generator[str]:
        processed_ids = set()

        for extracted_ids in batched(self.concept_ids_from_works(), 10_000, strict=False):
            same_as_result = self.get_same_as_redirect(extracted_ids)
            for concept_id in extracted_ids:
                if concept_id not in processed_ids:
                    for same_as_id in same_as_result.get(concept_id, {}).get("same_as_ids", []) + [concept_id]:
                        processed_ids.add(same_as_id)
                        yield same_as_id
                # query = """
        #     MATCH (concept:Concept)
        #     WITH concept ORDER BY concept.id
        #     SKIP 0 LIMIT 10000
        #     RETURN concept.id AS id
        # """
        # 
        # result = self.neptune_client.run_open_cypher_query(query)
        # for i in result:
        #     self.redirect_map[i["id"]] = i["id"]
        #     yield i["id"]
                  
    def extract_raw(self) -> Generator[tuple]:
        for concept_ids in batched(self.same_as_concepts(), 10_000, strict=False):
            concepts = self.get_concepts(concept_ids).items()

            with ThreadPoolExecutor() as executor:
                futures = {}
                for query in get_args(RelatedQuery):
                    futures[query] = executor.submit(self.get_related_concepts, query, concept_ids)
                for query in get_args(ReferencedTogetherQuery):
                    futures[query] = executor.submit(self.get_referenced_together_concepts, query, concept_ids)                    

                all_related_concepts = {key: future.result() for key, future in futures.items()}
        
            for concept_id, concept in concepts:
                related_concepts = {}
                for key in all_related_concepts:
                    if concept_id in all_related_concepts[key]:
                        related_concepts[key] = all_related_concepts[key][concept_id]
                
                yield concept, related_concepts
