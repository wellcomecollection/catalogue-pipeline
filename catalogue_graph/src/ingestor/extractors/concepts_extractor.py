from collections import defaultdict
from collections.abc import Generator
from queue import Queue
from typing import Iterable

from models.events import IncrementalWindow
from sources.catalogue.concepts_source import extract_concepts_from_work
from sources.threaded_es_source import ThreadedElasticsearchSource
from utils.aws import get_neptune_client

from ingestor.models.neptune.query_result import NeptuneConcept
from ingestor.queries.concept_queries import (
    CONCEPT_QUERY,
    CONCEPT_TYPE_QUERY,
    REFERENCED_TOGETHER_QUERY,
    SAME_AS_CONCEPT_QUERY,
    SOURCE_CONCEPT_QUERY,
    get_related_query,
)

# Maximum number of related nodes to return for each relationship type
RELATED_TO_LIMIT = 10

# Minimum number of works in which two concepts must co-occur to be considered 'frequently referenced together'
NUMBER_OF_SHARED_WORKS_THRESHOLD = 3

# There are a few Wikidata supernodes which cause performance issues in queries.
# We need to filter them out when running queries to get related nodes.
# Q5 -> 'human', Q151885 -> 'concept'
IGNORED_WIKIDATA_IDS = ["Q5", "Q151885"]


LinkedConcepts = dict[str, list[dict]]


def _related_query_result_to_dict(related_to: list[dict]) -> LinkedConcepts:
    """
    Transform a list of dictionaries mapping a concept ID to a list of related concepts into a single dictionary
    (with concept IDs as keys and related concepts as values).
    """
    return {item["id"]: item["related"] for item in related_to}


class GraphConceptsExtractor(ThreadedElasticsearchSource):
    def __init__(self, pipeline_date: str, window: IncrementalWindow = None, is_local: bool = False):
        super().__init__(pipeline_date=pipeline_date, window=window, is_local=is_local)
        self.query = {"match": {"type": "Visible"}}
        self.neptune_client = get_neptune_client(is_local)
            
        # TODO: Use locking
        self.processed_concepts = set()
        self.redirect_map = {}

    def get_neptune_query_params(self, ids: list[str]) -> dict:
        return {
            "ids": ids,
            "ignored_wikidata_ids": IGNORED_WIKIDATA_IDS,
            "related_to_limit": RELATED_TO_LIMIT,
            "shared_works_count_threshold": NUMBER_OF_SHARED_WORKS_THRESHOLD,
        }

    def make_neptune_query(self, query: str, ids: Iterable[str], label: str, params: dict | None = None) -> dict:
        params = {**self.get_neptune_query_params(list(ids)), **(params or {})}
        result = self.neptune_client.time_open_cypher_query(query, params, label)
        
        return {item['id']: item for item in result}
    
    def get_concept_types(self, ids: Iterable[str]):
        result = self.make_neptune_query(SAME_AS_CONCEPT_QUERY, ids, 'same as')
        
        all_ids = set(ids)
        for item in result.values():
            all_ids.update(item['same_as_ids'])

        types_result = self.make_neptune_query(CONCEPT_TYPE_QUERY, all_ids, 'concept type')
        
        concept_types = defaultdict(set)
        for concept_id in result:
            for same_as_concept_id in result[concept_id]['same_as_ids']:
                concept_types[concept_id].update(types_result[same_as_concept_id]['types'])
        
        return concept_types

    def get_concepts(self, ids: Iterable[str]):
        concepts = self.make_neptune_query(CONCEPT_QUERY, ids, 'concept')
        source_concepts = self.make_neptune_query(SOURCE_CONCEPT_QUERY, ids, 'source concept')
        same_as_concepts = self.make_neptune_query(SAME_AS_CONCEPT_QUERY, ids, 'same as')
        concept_types = self.get_concept_types(ids)
        
        full_concepts = {}
        for concept_id in concepts:
            neptune_concept = NeptuneConcept(
                concept=concepts[concept_id]["concept"],
                source_concepts=source_concepts.get(concept_id, {}).get("source_concepts", []),
                types=concept_types.get(concept_id, ["Concept"]),
                same_as=same_as_concepts.get(concept_id, {}).get("same_as_ids", []),
                linked_source_concept=None
            )
            full_concepts[concept_id] = neptune_concept
        
        return full_concepts

    def get_same_as_redirect(self, ids):
        result = self.make_neptune_query(SAME_AS_CONCEPT_QUERY, ids, 'same as')
    
        redirect_map = {i: i for i in ids}
        for concept_id, item in result.items():
            # Alphabetical ID-based prioritisation
            all_same_as = [concept_id] + item['same_as_ids']
            redirect_id = sorted(all_same_as)[0]
            
            redirect_map[concept_id] = redirect_id
            for same_as_id in item['same_as_ids']:
                redirect_map[same_as_id] = redirect_id
    
        return redirect_map

    def get_related_concepts(self, ids: list[str]) -> LinkedConcepts:
        query = get_related_query("RELATED_TO")
        result = self.make_neptune_query(query, ids, "related to")
        return _related_query_result_to_dict(result)

    def get_field_of_work_concepts(self, ids: list[str]) -> LinkedConcepts:
        query = get_related_query("HAS_FIELD_OF_WORK")
        result = self.make_neptune_query(query, ids, "field of work")
        return _related_query_result_to_dict(result)

    def get_narrower_concepts(self, ids: list[str]) -> LinkedConcepts:
        query = get_related_query("NARROWER_THAN")
        result = self.make_neptune_query(query, ids, "narrower than")
        return _related_query_result_to_dict(result)

    def get_broader_concepts(self, ids: list[str]) -> LinkedConcepts:
        query = get_related_query("NARROWER_THAN|HAS_PARENT", "to")
        result = self.make_neptune_query(query, ids, "broader than")
        return _related_query_result_to_dict(result)

    def get_people_concepts(self, ids: list[str]) -> LinkedConcepts:
        query = get_related_query("HAS_FIELD_OF_WORK", "to")
        result = self.make_neptune_query(query, ids, "people")
        return _related_query_result_to_dict(result)

    def get_collaborator_concepts(self, ids: list[str]) -> LinkedConcepts:
        redirect_to = self.get_same_as_redirect(ids)
        all_ids = redirect_to.keys()

        # Retrieve people and orgs which are commonly referenced together as collaborators with a given person/org
        params = {
            "referenced_in": ["contributors"],
            "referenced_type": ["Person", "Organisation"],
            "related_referenced_in": ["contributors"],
            "related_referenced_type": ["Person", "Organisation"]
        }
        result = self.make_neptune_query(REFERENCED_TOGETHER_QUERY, all_ids, "frequent collaborators", params)

        related_ids = set()
        for item in result.values():
            for related in item['related']:
                related_ids.add(related['id'])
        
        related_redirect_to = self.get_same_as_redirect(related_ids)
        
        # Merge results across *same as* concepts and related concepts
        merged_frequent_collaborators = defaultdict(lambda: defaultdict(lambda: 0))
        for concept_id, item in result.items():
            primary_id = redirect_to[concept_id]
        
            for related in item["related"]:
                primary_related_id = related_redirect_to[related['id']]
                
                # Make sure a concept does not reference itself
                if redirect_to[primary_id] != related_redirect_to[primary_related_id]:
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
            for related_id in full_frequent_collaborators[concept_id]:
                full_frequent_collaborators[concept_id].append(full_related_concepts[related_id])
            
        return full_frequent_collaborators

    def get_related_topics(self, ids: list[str]) -> LinkedConcepts:
        # Do not include agents/people/orgs in the list of related topics.
        params = {
            "referenced_in": ["subjects", "contributors", "genres"],
            "referenced_type": ["Organisation", "Agent", "Person", "Concept", "Subject", "Place", "Meeting", "Period", "Genre"],
            "related_referenced_in": ["subjects"],
            "related_referenced_type": ["Concept", "Subject", "Place", "Meeting", "Period", "Genre"]
        }
        result = self.make_neptune_query(REFERENCED_TOGETHER_QUERY, ids, "related topics", params)
        return _related_query_result_to_dict(result)

    def worker_target(self, slice_index: int, queue: Queue) -> None:
        search_after = None
        while hits := self.search(slice_index, search_after):
            # TODO: Error handling in threads!
            concept_ids = set()
            for hit in hits:
                for concept, _ in extract_concepts_from_work(hit["_source"]["data"]):
                    concept_id = concept["id"]["canonicalId"]
                    if concept_id not in self.processed_concepts:
                        self.processed_concepts.add(concept_id)
                        concept_ids.add(concept_id)

            concept_ids = list(concept_ids)
            all_related_concepts = {
                # "related_to": self.get_related_concepts(concept_ids),
                # "fields_of_work": self.get_field_of_work_concepts(concept_ids),
                # "narrower_than": self.get_narrower_concepts(concept_ids),
                # "broader_than": self.get_broader_concepts(concept_ids),
                # "people": self.get_people_concepts(concept_ids),
                "frequent_collaborators": self.get_collaborator_concepts(concept_ids),
                #"related_topics": self.get_related_topics(concept_ids),
            }

            for concept_id, concept in self.get_concepts(concept_ids).items():
                related_concepts = {}
                for key in all_related_concepts:
                    if concept_id in all_related_concepts[key]:
                        related_concepts[key] = all_related_concepts[key][concept_id]
    
                queue.put((concept, related_concepts))

            search_after = hits[-1]["sort"]

        queue.put(None)

    def extract_raw(self) -> Generator[tuple]:
        yield from self.stream_raw()
