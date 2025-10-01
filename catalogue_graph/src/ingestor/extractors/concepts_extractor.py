from collections.abc import Generator

from ingestor.queries.concept_queries import (
    CONCEPT_QUERY,
    get_referenced_together_query,
    get_related_query,
)

from .base_extractor import GraphBaseExtractor

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


class GraphConceptsExtractor(GraphBaseExtractor):
    def get_concepts(self) -> list[dict]:
        return self.make_neptune_query(CONCEPT_QUERY, "concept")

    def get_related_concepts(self) -> LinkedConcepts:
        query = get_related_query("RELATED_TO")
        result = self.make_neptune_query(query, "related to")
        return _related_query_result_to_dict(result)

    def get_field_of_work_concepts(self) -> LinkedConcepts:
        query = get_related_query("HAS_FIELD_OF_WORK")
        result = self.make_neptune_query(query, "field of work")
        return _related_query_result_to_dict(result)

    def get_founded_by_concepts(self) -> LinkedConcepts:
        query = get_related_query("HAS_FOUNDER")
        result = self.make_neptune_query(query, "founded by")
        return _related_query_result_to_dict(result)

    def get_narrower_concepts(self) -> LinkedConcepts:
        query = get_related_query("NARROWER_THAN")
        result = self.make_neptune_query(query, "narrower than")
        return _related_query_result_to_dict(result)

    def get_broader_concepts(self) -> LinkedConcepts:
        query = get_related_query("NARROWER_THAN|HAS_PARENT", "to")
        result = self.make_neptune_query(query, "broader than")
        return _related_query_result_to_dict(result)

    def get_people_concepts(self) -> LinkedConcepts:
        query = get_related_query("HAS_FIELD_OF_WORK", "to")
        result = self.make_neptune_query(query, "people")
        return _related_query_result_to_dict(result)

    def get_collaborator_concepts(self) -> LinkedConcepts:
        # Retrieve people and orgs which are commonly referenced together as collaborators with a given person/org
        query = get_referenced_together_query(
            source_referenced_types=["Person", "Organisation"],
            related_referenced_types=["Person", "Organisation"],
            source_referenced_in=["contributors"],
            related_referenced_in=["contributors"],
        )
        result = self.make_neptune_query(query, "frequent collaborators")
        return _related_query_result_to_dict(result)

    def get_related_topics(self) -> LinkedConcepts:
        # Do not include agents/people/orgs in the list of related topics.
        query = get_referenced_together_query(
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
        result = self.make_neptune_query(query, "related topics")
        return _related_query_result_to_dict(result)

    def get_neptune_query_params(self) -> dict:
        return {
            **super().get_neptune_query_params(),
            "ignored_wikidata_ids": IGNORED_WIKIDATA_IDS,
            "related_to_limit": RELATED_TO_LIMIT,
            "number_of_shared_works_threshold": NUMBER_OF_SHARED_WORKS_THRESHOLD,
        }

    def extract_raw(self) -> Generator[tuple[dict, dict]]:
        all_related_concepts = {
            "related_to": self.get_related_concepts(),
            "fields_of_work": self.get_field_of_work_concepts(),
            "founded_by": self.get_founded_by_concepts(),
            "narrower_than": self.get_narrower_concepts(),
            "broader_than": self.get_broader_concepts(),
            "people": self.get_people_concepts(),
            "frequent_collaborators": self.get_collaborator_concepts(),
            "related_topics": self.get_related_topics(),
        }

        for concept in self.get_concepts():
            concept_id = concept["concept"]["~properties"]["id"]
            related_concepts = {}
            for key in all_related_concepts:
                if concept_id in all_related_concepts[key]:
                    related_concepts[key] = all_related_concepts[key][concept_id]

            yield concept, related_concepts
