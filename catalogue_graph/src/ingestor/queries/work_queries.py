WORK_QUERY = """
        MATCH (work:Work)
        WITH work ORDER BY id(work)
        SKIP $start_offset LIMIT $limit
        
        RETURN work.id AS id
"""

WORK_HIERARCHY_QUERY = """
        MATCH (work:Work)
        WITH work ORDER BY id(work)
        SKIP $start_offset LIMIT $limit

        MATCH (work)-[:HAS_PATH_IDENTIFIER]->(identifier)
        OPTIONAL MATCH (identifier)-[:HAS_PARENT*]->(ancestor_identifier)<-[:HAS_PATH_IDENTIFIER]-(ancestor_work)
        OPTIONAL MATCH (identifier)<-[:HAS_PARENT]-(child_identifier)<-[:HAS_PATH_IDENTIFIER]-(child_work)
        OPTIONAL MATCH (child_identifier)<-[:HAS_PARENT]-(grandchild_identifier)
        
        WITH work, ancestor_work, child_work, COUNT(grandchild_identifier) AS child_work_parts
        WITH work,
             COLLECT(ancestor_work) AS ancestor_works,        
             COLLECT(
                 CASE 
                     WHEN child_work IS NOT NULL THEN { work: child_work, parts: child_work_parts }
                 END
             ) AS child_works
        
        RETURN 
            work.id AS id,
            [work IN ancestor_works WHERE work IS NOT NULL] AS ancestor_works,
            [work IN child_works WHERE work IS NOT NULL] AS children
"""

WORK_CONCEPTS_QUERY = """
        MATCH (work:Work)
        WITH work ORDER BY id(work)
        SKIP $start_offset LIMIT $limit
        
        MATCH (work)-[:HAS_CONCEPT]->(concept)
        OPTIONAL MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)
        OPTIONAL MATCH (linked_source_concept)-[:SAME_AS*1..2]->(source_concept)
        WHERE linked_source_concept.id <> source_concept.id
        
        WITH
            work,
            concept,
            linked_source_concept,
            COLLECT(source_concept) AS other_source_concepts

        RETURN 
            work.id AS id,
            COLLECT({
                concept: concept,
                linked_source_concept: linked_source_concept,
                other_source_concepts: other_source_concepts
            }) AS concepts
"""
