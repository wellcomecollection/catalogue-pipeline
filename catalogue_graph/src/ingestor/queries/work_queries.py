WORK_ANCESTORS_QUERY = """
        UNWIND $ids AS id
        MATCH (work:Work {`~id`: id})

        MATCH (work)-[:HAS_PATH_IDENTIFIER]->(identifier)
        MATCH path = (identifier)-[:HAS_PARENT*]->(ancestor_identifier)
        MATCH (ancestor_identifier)<-[:HAS_PATH_IDENTIFIER]-(ancestor_work)
        MATCH (ancestor_identifier)<-[:HAS_PARENT]-(ancestor_tree_identifier)

        WITH work.id AS id, ancestor_work, length(path) AS hops, COUNT(ancestor_tree_identifier) AS ancestor_parts
        ORDER BY hops ASC
        
        RETURN id, COLLECT({ work: ancestor_work, parts: ancestor_parts }) AS ancestors;
"""

WORK_CHILDREN_QUERY = """
        UNWIND $ids AS id
        MATCH (work:Work {`~id`: id})

        MATCH (work)-[:HAS_PATH_IDENTIFIER]->(identifier)
        MATCH (identifier)<-[:HAS_PARENT]-(child_identifier)<-[:HAS_PATH_IDENTIFIER]-(child_work)
        OPTIONAL MATCH (child_identifier)<-[:HAS_PARENT]-(grandchild_identifier)
        
        WITH work, child_work, COUNT(grandchild_identifier) AS child_work_parts
        RETURN work.id AS id, COLLECT({ work: child_work, parts: child_work_parts }) AS children
"""

WORK_CONCEPTS_QUERY = """
        UNWIND $ids AS id
        MATCH (work:Work {`~id`: id})
        
        MATCH (work)-[:HAS_CONCEPT]->(concept)
        OPTIONAL MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)
        OPTIONAL MATCH (linked_source_concept)-[:SAME_AS*0..2]->(source_concept)
        
        WITH
            work,
            concept,
            linked_source_concept,
            COLLECT(DISTINCT source_concept) AS source_concepts

        RETURN
            work.id AS id,
            COLLECT({
                concept: concept,
                linked_source_concept: linked_source_concept,
                source_concepts: source_concepts
            }) AS concepts
"""
