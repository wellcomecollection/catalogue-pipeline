import time
from collections.abc import Generator, Iterable
from typing import Any, Literal

from ingestor.queries.concept_queries import (
    CONCEPT_QUERY,
    CONCEPT_TYPE_QUERY,
    SAME_AS_CONCEPT_QUERY,
    SOURCE_CONCEPT_QUERY,
    get_referenced_together_query,
    get_related_query,
)
from ingestor.queries.work_queries import (
    WORK_ANCESTORS_QUERY,
    WORK_CHILDREN_QUERY,
    WORK_CONCEPTS_QUERY,
)
from utils.aws import get_neptune_client
from utils.streaming import process_stream_in_parallel

ConceptRelatedQuery = Literal[
    "related_to", "fields_of_work", "narrower_than", "broader_than", "people"
]
ConceptReferencedTogetherQuery = Literal["frequent_collaborators", "related_topics"]
ConceptQuery = Literal[
    "concept",
    "concept_type",
    "source_concept",
    "same_as_concept",
    ConceptRelatedQuery,
    ConceptReferencedTogetherQuery,
]
WorkQuery = Literal["work_children", "work_ancestors", "work_concepts"]


EXPENSIVE_QUERIES = {"related_topics", "frequent_collaborators"}

NEPTUNE_QUERIES = {
    "work_children": WORK_CHILDREN_QUERY,
    "work_ancestors": WORK_ANCESTORS_QUERY,
    "work_concepts": WORK_CONCEPTS_QUERY,
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
    ),
}


class GraphBaseExtractor:
    def __init__(self, is_local: bool = False):
        self.neptune_client = get_neptune_client(is_local)
        self.neptune_params: dict[str, Any] = {}

    def extract_raw(self) -> Generator[Any]:
        """Returns a generator of raw data corresponding to items extracted from the catalogue graph."""
        raise NotImplementedError(
            "Each extractor must implement an `extract_raw` method."
        )

    def make_neptune_query(
        self, query_type: ConceptQuery | WorkQuery, ids: Iterable[str]
    ) -> dict:
        chunk_size = 1000 if query_type in EXPENSIVE_QUERIES else 5000

        def _run_query(chunk: Iterable[str]) -> list[dict]:
            return self.neptune_client.run_open_cypher_query(
                NEPTUNE_QUERIES[query_type], self.neptune_params | {"ids": chunk}
            )

        start = time.time()
        raw_result = process_stream_in_parallel(ids, _run_query, chunk_size, 5)
        results = {item["id"]: item for item in raw_result}

        print(
            f"Ran a set of '{query_type}' queries in {round(time.time() - start)}s, "
            f"retrieving {len(results)} records."
        )
        return results
