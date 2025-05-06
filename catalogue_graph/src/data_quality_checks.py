from models.graph_node import ConceptType
from utils.aws import get_neptune_client

LIMIT = 10000

client = get_neptune_client(True)


def are_concept_types_consistent(concept_types: list[ConceptType]) -> bool:
    # 'Concept' and 'Subject' types are consistent with all other types,
    # so we filter them out when determining consistency
    filtered_types = [concept_type for concept_type in concept_types if concept_type not in ('Concept', 'Subject')]

    if len(filtered_types) <= 1:
        return True

    # Of all remaining types, only Agent/Org and Agent/Person are compatible. All other combinations are invalid.
    compatible_combinations = [{'Agent', 'Organisation'}, {'Agent', 'Person'}]
    return set(filtered_types) in compatible_combinations


count_query = "MATCH (c:Concept) RETURN count(c) AS count"
concepts_count = client.run_open_cypher_query(count_query)[0]["count"]

print(concepts_count)

with open("invalid.txt", "w") as f:
    start_offset = 0
    while start_offset < concepts_count:
        query = """
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
        print(start_offset)

        concept_result = client.run_open_cypher_query(query, {"start_offset": start_offset, "limit": LIMIT})
        for concept in concept_result:
            if not are_concept_types_consistent(concept["concept_types"]):
                f.write(f"{concept['concept']['id']} {concept['concept']['label']} {concept['concept_types']}\n")
        f.flush()
        start_offset += LIMIT
