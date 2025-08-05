WORK_QUERY = """
        MATCH (work:Work)
        WITH work ORDER BY id(work)
        SKIP $start_offset LIMIT $limit
        
        RETURN work
"""

WORK_HIERARCHY_QUERY = """
        MATCH (work:Work)
        WITH work ORDER BY id(work)
        SKIP $start_offset LIMIT $limit

        MATCH (work)-[:HAS_SOURCE_IDENTIFIER]->(identifier)
        MATCH (identifier)-[:HAS_PARENT]->(parent_identifier)<-[:HAS_SOURCE_IDENTIFIER]-(parent_work)
        OPTIONAL MATCH (identifier)<-[:HAS_PARENT]-(child_identifier)<-[:HAS_SOURCE_IDENTIFIER]-(child_work)
        OPTIONAL MATCH (child_identifier)<-[:HAS_PARENT]-(grandchild_identifier)
        
        WITH work, parent_work, child_work, COUNT(grandchild_identifier) AS child_work_parts
        
        RETURN 
            work.id AS id,
            {title: parent_work.label, id: parent_work.id, reference: parent_work.reference_number} AS parent,
            COLLECT({
                title: child_work.label,
                id: child_work.id,
                reference: child_work.reference_number,
                total_parts: child_work_parts
            }) AS children
"""

WORK_CONCEPTS_QUERY = """
        MATCH (work:Work)
        WITH work ORDER BY id(work)
        SKIP $start_offset LIMIT $limit
        
        MATCH (work)-[has_concept_edge:HAS_CONCEPT]->(concept)
        OPTIONAL MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)
        OPTIONAL MATCH (linked_source_concept)-[:SAME_AS*1..2]->(source_concept)
        WHERE linked_source_concept.id <> source_concept.id
        
        WITH
            work,
            has_concept_edge,
            concept,
            linked_source_concept,
            COLLECT(source_concept) AS other_source_concepts

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
