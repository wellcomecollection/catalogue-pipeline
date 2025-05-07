import csv

import boto3
import smart_open

import config
from models.graph_node import ConceptType
from utils.aws import get_neptune_client

LIMIT = 20000

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


def check_concept_type_consistency() -> None:
    count_query = "MATCH (c:Concept) RETURN count(c) AS count"
    client = get_neptune_client(True)
    concepts_count = client.run_open_cypher_query(count_query)[0]["count"]

    transport_params = {"client": boto3.client("s3")}
    s3_uri = f"s3://{config.INGESTOR_S3_BUCKET}/data_quality_checks/inconsistent_concept_types.csv"
    with smart_open.open(s3_uri, "w", transport_params=transport_params) as f:
        csv_writer = None

        start_offset = 0
        while start_offset < concepts_count:
            params = {"start_offset": start_offset, "limit": LIMIT}
            response = client.run_open_cypher_query(CONCEPT_TYPES_QUERY, params)

            for concept in response:
                concept_properties = concept["concept"]["~properties"]
                concept_types = concept["concept_types"]

                if not are_concept_types_consistent(concept_types):
                    invalid_item = {
                        "concept_id": concept_properties["id"],
                        "concept_label": concept_properties["label"],
                        "concept_types": "||".join(concept_types),
                    }

                    if csv_writer is None:
                        csv_writer = csv.DictWriter(f, fieldnames=invalid_item.keys())
                        csv_writer.writeheader()

                    csv_writer.writerow(invalid_item)

            start_offset += LIMIT


if __name__ == "__main__":
    check_concept_type_consistency()
