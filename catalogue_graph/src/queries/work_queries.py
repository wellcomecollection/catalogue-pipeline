from clients.base_neptune_client import BaseNeptuneClient

WORK_QUERY = """
        MATCH (work:Work)
        WITH work ORDER BY work.id
        SKIP $start_offset LIMIT $limit
        
        MATCH (work)-[:HAS_SOURCE_IDENTIFIER]->(identifier)
        
        RETURN work, COLLECT(identifier) AS identifiers
"""

WORK_HIERARCHY_QUERY = """
        MATCH (work:Work)
        WITH work ORDER BY work.id
        SKIP $start_offset LIMIT $limit

        MATCH (work)-[:HAS_SOURCE_IDENTIFIER]->(identifier)
        MATCH (identifier)-[:HAS_PARENT]->(parent_identifier)<-[:HAS_SOURCE_IDENTIFIER]-(parent_work)
        OPTIONAL MATCH (parent_identifier)<-[:HAS_PARENT]-(sibling_identifier)<-[:HAS_SOURCE_IDENTIFIER]-(sibling_work)
        WHERE sibling_work.id <> work.id

        RETURN 
            work.id AS id,
            {title: parent_work.label, id: parent_work.id, reference: parent_work.reference_number} AS parent,
            COLLECT({title: sibling_work.label, id: sibling_work.id, reference: sibling_work.reference_number}) AS siblings
"""

WORK_CONCEPTS_QUERY = """
        MATCH (work:Work)
        WITH work ORDER BY work.id
        SKIP $start_offset LIMIT $limit
        
        MATCH (work)-[has_concept_edge:HAS_CONCEPT]->(concept)
        OPTIONAL MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)-[:SAME_AS*1..2]->(source_concept)
        WHERE linked_source_concept.id <> source_concept.id
        
        WITH
            work,
            has_concept_edge,
            {id: concept.id, label: concept.label} AS concept,
            {id: linked_source_concept.id, label: linked_source_concept.label} AS linked_source_concept,
            COLLECT({id: source_concept.id, label: source_concept.label}) AS other_source_concepts

        RETURN 
            work.id AS id,
            COLLECT({
                concept: concept,
                referenced_in: has_concept_edge.referenced_in,
                referenced_type: has_concept_edge.referenced_type,
                linked_source_concept: linked_source_concept,
                other_source_concepts: other_source_concepts
            }) AS concepts
"""


def get_works(client: BaseNeptuneClient, params: dict) -> list[dict]:
    return client.time_open_cypher_query(WORK_QUERY, params, "works")


def get_work_hierarchy(client: BaseNeptuneClient, params: dict) -> dict:
    results = client.time_open_cypher_query(
        WORK_HIERARCHY_QUERY, params, "work hierarchy"
    )
    return {
        item["id"]: {"parent": item["parent"], "siblings": item["siblings"]}
        for item in results
    }


def get_work_concepts(client: BaseNeptuneClient, params: dict) -> dict:
    results = client.time_open_cypher_query(
        WORK_CONCEPTS_QUERY, params, "work concepts"
    )
    return {item["id"]: item["concepts"] for item in results}
