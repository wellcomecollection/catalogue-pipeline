import time
from collections.abc import Generator, Iterable
from typing import Any, Literal

from ingestor.queries.concept_queries import (
    BROADER_THAN_QUERY,
    CONCEPT_QUERY,
    CONCEPT_TYPE_QUERY,
    FIELDS_OF_WORK_QUERY,
    FREQUENT_COLLABORATORS_QUERY,
    HAS_FOUNDER_QUERY,
    NARROWER_THAN_QUERY,
    PEOPLE_QUERY,
    RELATED_TO_QUERY,
    RELATED_TOPICS_QUERY,
    SAME_AS_CONCEPT_QUERY,
    SOURCE_CONCEPT_QUERY,
)
from ingestor.queries.work_queries import (
    WORK_ANCESTORS_QUERY,
    WORK_CHILDREN_QUERY,
    WORK_CONCEPTS_QUERY,
)
from utils.aws import get_neptune_client
from utils.streaming import process_stream_in_parallel

ConceptRelatedQuery = Literal[
    "related_to",
    "fields_of_work",
    "narrower_than",
    "broader_than",
    "people",
    "founded_by",
    "frequent_collaborators",
    "related_topics",
]
ConceptQuery = Literal[
    "concept",
    "concept_type",
    "source_concept",
    "same_as_concept",
    ConceptRelatedQuery,
]
WorkQuery = Literal["work_children", "work_ancestors", "work_concepts"]

NEPTUNE_CHUNK_SIZE = 5000

# Computationally expensive queries work more reliably with smaller chunk sizes
EXPENSIVE_QUERIES = {"related_topics", "frequent_collaborators"}
NEPTUNE_EXPENSIVE_CHUNK_SIZE = 1000

NEPTUNE_QUERIES: dict[ConceptQuery | WorkQuery, str] = {
    "work_children": WORK_CHILDREN_QUERY,
    "work_ancestors": WORK_ANCESTORS_QUERY,
    "work_concepts": WORK_CONCEPTS_QUERY,
    "concept": CONCEPT_QUERY,
    "concept_type": CONCEPT_TYPE_QUERY,
    "source_concept": SOURCE_CONCEPT_QUERY,
    "same_as_concept": SAME_AS_CONCEPT_QUERY,
    "related_to": RELATED_TO_QUERY,
    "fields_of_work": FIELDS_OF_WORK_QUERY,
    "narrower_than": NARROWER_THAN_QUERY,
    "broader_than": BROADER_THAN_QUERY,
    "people": PEOPLE_QUERY,
    "frequent_collaborators": FREQUENT_COLLABORATORS_QUERY,
    "related_topics": RELATED_TOPICS_QUERY,
    "founded_by": HAS_FOUNDER_QUERY,
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
    ) -> dict[str, dict]:
        """
        Split the specified ids into chunks and run the selected query against each chunk.
        Results are returned as a dictionary mapping each id to its corresponding result.
        """
        chunk_size = NEPTUNE_CHUNK_SIZE
        if query_type in EXPENSIVE_QUERIES:
            chunk_size = NEPTUNE_EXPENSIVE_CHUNK_SIZE

        def _run_query(chunk: Iterable[str]) -> list[dict]:
            return self.neptune_client.run_open_cypher_query(
                NEPTUNE_QUERIES[query_type],
                self.neptune_params | {"ids": sorted(chunk)},
            )

        start = time.time()
        raw_results = process_stream_in_parallel(ids, _run_query, chunk_size, 5)
        results = {item["id"]: item for item in raw_results}

        print(
            f"Ran a set of '{query_type}' queries in {round(time.time() - start)}s, "
            f"retrieving {len(results)} records."
        )
        return results
