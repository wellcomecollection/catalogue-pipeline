from collections.abc import Generator

from clients.neptune_client import NeptuneClient
from utils.types import ConceptType

CONCEPT_TYPES_QUERY_LIMIT = 20000
CONCEPT_TYPES_QUERY = """
    MATCH (concept:Concept)
    WITH concept ORDER BY concept.id
    SKIP $start_offset LIMIT $limit
    OPTIONAL MATCH (work)-[has_concept:HAS_CONCEPT]-(concept)
    WITH 
        concept,
        collect(DISTINCT has_concept.referenced_type) AS concept_types
    RETURN
        concept,
        concept_types        
"""


def _are_concept_types_consistent(concept_types: list[ConceptType]) -> bool:
    """Return `True` if all provided concept types are mutually compatible. Otherwise, return `False`."""
    # 'Concept' and 'Subject' types are consistent with all other types,
    # so we filter them out when determining consistency
    filtered_types = [c for c in concept_types if c not in ("Concept", "Subject")]

    if len(filtered_types) <= 1:
        return True

    # Of all remaining types, only Agent/Org and Agent/Person are compatible. All other combinations are invalid.
    compatible_combinations = [{"Agent", "Organisation"}, {"Agent", "Person"}]
    return set(filtered_types) in compatible_combinations


def _get_concepts_count() -> int:
    count_query = "MATCH (c:Concept) RETURN count(c) AS count"
    response = NeptuneClient().run_open_cypher_query(count_query)
    count: int = response[0]["count"]
    return count


def get_concepts_with_inconsistent_types() -> Generator[dict]:
    """Return all concepts whose combination of types is not consistent."""
    client = NeptuneClient()

    start_offset = 0
    concepts_count = _get_concepts_count()
    while start_offset < concepts_count:
        params = {"start_offset": start_offset, "limit": CONCEPT_TYPES_QUERY_LIMIT}
        response = client.run_open_cypher_query(CONCEPT_TYPES_QUERY, params)

        for concept in response:
            if not _are_concept_types_consistent(concept["concept_types"]):
                yield {
                    "concept_id": concept["concept"]["~properties"]["id"],
                    "concept_label": concept["concept"]["~properties"]["label"],
                    "concept_types": "||".join(concept["concept_types"]),
                }

        start_offset += CONCEPT_TYPES_QUERY_LIMIT
