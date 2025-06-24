
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
            work.id,
            {title: parent_work.label, id: parent_work.id} AS parent_work,
            COLLECT({title: sibling_work.label, id: sibling_work.id}) AS sibling_works
"""
