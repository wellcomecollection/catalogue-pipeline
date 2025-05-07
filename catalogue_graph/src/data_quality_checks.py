import config
from models.graph_node import ConceptType
from utils.aws import get_neptune_client, write_csv_to_s3

S3_DATA_QUALITY_CHECKS_PREFIX = f"s3://{config.INGESTOR_S3_BUCKET}/data_quality_checks"

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

CONCEPT_TYPES_QUERY_LIMIT = 20000


def are_concept_types_consistent(concept_types: list[ConceptType]) -> bool:
    """Return `True` if all provided concept types are mutually compatible. Otherwise, return `False`."""
    # 'Concept' and 'Subject' types are consistent with all other types,
    # so we filter them out when determining consistency
    filtered_types = [c for c in concept_types if c not in ("Concept", "Subject")]

    if len(filtered_types) <= 1:
        return True

    # Of all remaining types, only Agent/Org and Agent/Person are compatible. All other combinations are invalid.
    compatible_combinations = [{"Agent", "Organisation"}, {"Agent", "Person"}]
    return set(filtered_types) in compatible_combinations


def get_concepts_count():
    count_query = "MATCH (c:Concept) RETURN count(c) AS count"
    response = get_neptune_client(True).client.run_open_cypher_query(count_query)
    return response[0]["count"]


def check_concept_type_consistency() -> None:
    client = get_neptune_client(True)
    invalid_items = []

    start_offset = 0
    while start_offset < get_concepts_count():
        params = {"start_offset": start_offset, "limit": CONCEPT_TYPES_QUERY_LIMIT}
        response = client.run_open_cypher_query(CONCEPT_TYPES_QUERY, params)

        for concept in response:
            if not are_concept_types_consistent(concept["concept_types"]):
                invalid_items.append(
                    {
                        "concept_id": concept["concept"]["~properties"]["id"],
                        "concept_label": concept["concept"]["~properties"]["label"],
                        "concept_types": "||".join(concept["concept_types"]),
                    }
                )

        start_offset += CONCEPT_TYPES_QUERY_LIMIT

    s3_uri = f"{S3_DATA_QUALITY_CHECKS_PREFIX}/inconsistent_concept_types.csv"
    write_csv_to_s3(s3_uri, invalid_items)


if __name__ == "__main__":
    check_concept_type_consistency()
