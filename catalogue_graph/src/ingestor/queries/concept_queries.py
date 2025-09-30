from utils.types import ConceptType, WorkConceptKey

CONCEPT_QUERY = """
    UNWIND $ids AS id
    MATCH (concept:Concept {`~id`: id})
    RETURN concept.id AS id, concept
"""

CONCEPT_TYPE_QUERY = """
    UNWIND $ids AS id
    MATCH (concept:Concept {`~id`: id})
    MATCH (concept)<-[has_concept:HAS_CONCEPT]-(work)

    WITH DISTINCT concept, has_concept.referenced_type AS concept_type
    RETURN concept.id AS id, COLLECT(concept_type) AS types
"""

SOURCE_CONCEPT_QUERY = """
    UNWIND $ids AS id
    MATCH (concept:Concept {`~id`: id})
    MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)-[:SAME_AS*0..]->(source_concept)

    RETURN
        concept.id AS id,
        collect(DISTINCT linked_source_concept) AS linked_source_concepts,
        collect(DISTINCT source_concept) AS source_concepts
"""

SAME_AS_CONCEPT_QUERY = """
    UNWIND $ids AS id
    MATCH (concept:Concept {`~id`: id})
    MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)-[:SAME_AS*0..]->(source_concept)
    MATCH (source_concept)<-[:HAS_SOURCE_CONCEPT]-(same_as_concept)
    WHERE same_as_concept <> concept

    RETURN
        concept.id AS id,
        collect(DISTINCT id(same_as_concept)) AS same_as_ids
"""


def get_related_query(
    edge_type: str,
    direction: str = "from",
    source_concept_label_types: list[str] | None = None,
) -> str:
    """Return a parameterized Neptune query to fetch related Wellcome concepts."""
    label_filter = ""
    if source_concept_label_types is not None and len(source_concept_label_types) > 0:
        label_filter = "WHERE " + " OR ".join(
            [f"linked_source_concept:{c}" for c in source_concept_label_types]
        )

    left_arrow = "<" if direction == "to" else ""
    right_arrow = ">" if direction == "from" else ""

    return f"""
        UNWIND $ids AS id
        MATCH (concept:Concept {{`~id`: id}})

        /* Match each concept to all of its source concepts */
        MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)-[:SAME_AS*0..2]->(source_concept)
        WHERE NOT source_concept.id IN $ignored_wikidata_ids

        WITH DISTINCT concept, linked_source_concept, source_concept

        /*
        Yield all related source concepts based on the specified relationship type and direction
        (e.g. `edge_type="NARROWER_THAN"` combined with `direction="from"` would yield broader source concepts).
        */  
        MATCH (source_concept){left_arrow}-[relationship_edge:{edge_type}]-{right_arrow}(linked_related_source_concept)
        WHERE NOT linked_related_source_concept.id IN $ignored_wikidata_ids
        AND NOT (linked_source_concept)-[:SAME_AS*0..2]->(linked_related_source_concept)

        WITH DISTINCT concept, relationship_edge, linked_related_source_concept

        MATCH (linked_related_source_concept)-[:SAME_AS*0..2]->(related_source_concept)
        WHERE NOT related_source_concept.id IN $ignored_wikidata_ids

        /* Get the Wellcome concept(s) associated with each related source concept. */
        MATCH (related_source_concept)<-[:HAS_SOURCE_CONCEPT]-(related_concept)
        MATCH (related_concept)<-[work_edge:HAS_CONCEPT]-(work)
        {label_filter}

        WITH concept,
            relationship_edge.relationship_type AS relationship_type,
            linked_related_source_concept,
            collect(related_concept.id) AS related_ids,
            COUNT(work) AS work_count
        
        WITH concept, relationship_type, head(related_ids) AS related_id, SUM(work_count) AS work_count
        ORDER BY work_count DESC

        RETURN
            concept.id AS id,
            collect({{
                id: related_id,
                relationship_type: relationship_type,
                count: work_count
            }})[0..$related_to_limit] AS related
    """


def _get_referenced_together_filter(
    property_key: str, allowed_values: list[ConceptType] | list[WorkConceptKey] | None
) -> str:
    """Return a Cypher filter in the form `AND property_key IN ['some allowed value', 'another value']`."""
    if allowed_values is not None and len(allowed_values) > 0:
        comma_separated_values = ", ".join([f"'{t}'" for t in allowed_values])
        return f"AND {property_key} IN [{comma_separated_values}]"

    return ""


def get_referenced_together_query(
    source_referenced_types: list[ConceptType] | None = None,
    related_referenced_types: list[ConceptType] | None = None,
    source_referenced_in: list[WorkConceptKey] | None = None,
    related_referenced_in: list[WorkConceptKey] | None = None,
) -> str:
    """
    Return a parameterized Neptune query to fetch concepts frequently co-occurring together in works.
    Args:
        source_referenced_types:
            Optional list of concept types for filtering 'main' concepts (for which related concepts will be retrieved)
        related_referenced_types:
            Optional list of concept types for filtering related concepts
        source_referenced_in:
            Optional list of work concept keys (e.g. 'genre', 'subject') for filtering 'main' concepts
        related_referenced_in:
            Optional list of work concept keys (e.g. 'genre', 'subject') for filtering related concepts
    """
    source_referenced_type_filter = _get_referenced_together_filter(
        "concept_edge.referenced_type", source_referenced_types
    )
    related_referenced_type_filter = _get_referenced_together_filter(
        "related_concept_edge.referenced_type", related_referenced_types
    )
    source_referenced_in_filter = _get_referenced_together_filter(
        "concept_edge.referenced_in", source_referenced_in
    )
    related_referenced_in_filter = _get_referenced_together_filter(
        "related_concept_edge.referenced_in", related_referenced_in
    )

    return f"""
        UNWIND $ids AS id
        MATCH (concept:Concept {{`~id`: id}})
    
        CALL {{
            WITH concept
            MATCH (concept)<-[concept_edge:HAS_CONCEPT]-(work:Work)
    
            WITH concept, concept_edge, work
            LIMIT 1000
            MATCH (work)-[related_concept_edge:HAS_CONCEPT]->(related_concept)
        
            WHERE id(concept) <> id(related_concept)
            {source_referenced_type_filter}
            {related_referenced_type_filter}
            {source_referenced_in_filter}
            {related_referenced_in_filter}
            
            WITH concept, related_concept, COUNT(work) AS shared_works_count
            WHERE shared_works_count >= $shared_works_count_threshold
        
            WITH concept, related_concept, shared_works_count
            ORDER BY shared_works_count DESC
    
            RETURN collect({{id: related_concept.id, count: shared_works_count}})[0..$related_to_limit] AS related
        }}
        
        RETURN concept.id AS id, related
    """


RELATED_TO_QUERY = get_related_query("RELATED_TO")
FIELDS_OF_WORK_QUERY = get_related_query("HAS_FIELD_OF_WORK")
NARROWER_THAN_QUERY = get_related_query("NARROWER_THAN")
BROADER_THAN_QUERY = get_related_query("NARROWER_THAN|HAS_PARENT", "to")
PEOPLE_QUERY = get_related_query("HAS_FIELD_OF_WORK", "to")

# Retrieve people and orgs which are commonly referenced together as collaborators with a given person/org
FREQUENT_COLLABORATORS_QUERY = get_referenced_together_query(
    source_referenced_types=["Person", "Organisation"],
    related_referenced_types=["Person", "Organisation"],
    source_referenced_in=["contributors"],
    related_referenced_in=["contributors"],
)

# Do not include agents/people/orgs in the list of related topics
RELATED_TOPICS_QUERY = get_referenced_together_query(
    related_referenced_types=[
        "Concept",
        "Subject",
        "Place",
        "Meeting",
        "Period",
        "Genre",
    ],
    related_referenced_in=["subjects"],
)
