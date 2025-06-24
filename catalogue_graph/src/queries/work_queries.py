WORK_QUERY = """
        MATCH (work:Work)
        WITH work ORDER BY work.id
        SKIP $start_offset LIMIT $limit
        RETURN work
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
            {title: parent_work.label, id: parent_work.id} AS parent_work,
            COLLECT({title: sibling_work.label, id: sibling_work.id}) AS sibling_works
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
