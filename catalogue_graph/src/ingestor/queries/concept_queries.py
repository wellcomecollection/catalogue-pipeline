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


REFERENCED_TOGETHER_QUERY = """
    UNWIND $ids AS id
    MATCH (concept:Concept {`~id`: id})

    CALL {
        WITH concept
        MATCH (concept)<-[concept_edge:HAS_CONCEPT]-(work:Work)

        WITH concept, concept_edge, work
        LIMIT 1000
        MATCH (work)-[related_concept_edge:HAS_CONCEPT]->(related_concept)

        WHERE concept_edge.referenced_in IN $referenced_in
        AND concept_edge.referenced_type IN $referenced_type
        AND related_concept_edge.referenced_in IN $related_referenced_in
        AND related_concept_edge.referenced_type IN $related_referenced_type

        WITH concept, related_concept, COUNT(work) AS shared_works_count
        WHERE shared_works_count >= $shared_works_count_threshold

        AND id(concept) <> id(related_concept)

        WITH concept, related_concept, shared_works_count
        ORDER BY shared_works_count DESC

        RETURN collect({id: related_concept.id, count: shared_works_count})[0..$related_to_limit] AS related
    }
    
    RETURN concept.id AS id, related
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
        
        /*
        Yield all related source concepts based on the specified relationship type and direction
        (e.g. `edge_type="NARROWER_THAN"` combined with `direction="from"` would yield broader source concepts).
        */
        MATCH (source_concept){left_arrow}-[rel:{edge_type}]-{right_arrow}(linked_related_source_concept)
        MATCH (linked_related_source_concept)-[:SAME_AS*0..2]->(related_source_concept)
        WHERE NOT linked_related_source_concept.id IN $ignored_wikidata_ids
            AND NOT related_source_concept.id IN $ignored_wikidata_ids
            AND NOT (linked_source_concept)-[:SAME_AS*0..2]->(related_source_concept)
        
        /* Get the Wellcome concept(s) associated with each related source concept. */
        MATCH (related_source_concept)<-[:HAS_SOURCE_CONCEPT]-(related_concept)
        MATCH (work)-[work_edge:HAS_CONCEPT]->(related_concept)
        {label_filter}
        
        /*
        Group the results into buckets, with one bucket for each combination of concept and related source concept.
        (Note that we do not create groups based on each `related_concept`, as that would cause duplicates in cases
        where two related concepts have the same source concept.)
        */
        WITH concept,
             linked_related_source_concept,
             COUNT(work) AS number_of_works,
             work_edge.referenced_type AS related_type,
             collect(DISTINCT related_source_concept) AS related_source_concepts,
             head(collect(related_concept)) AS selected_related_concept,
             head(collect(rel)) AS selected_related_edge
             
        /* Order the resulting related concepts based on popularity (i.e. the number of works in which they appear). */
        ORDER BY number_of_works DESC
        
        /* Collect distinct types of each `related` concept */
        WITH concept,
             linked_related_source_concept,
             number_of_works,
             collect(related_type) AS related_types,
             related_source_concepts,
             selected_related_concept,
             selected_related_edge        
        
        /*
        Group the results again to ensure that only one row is returned for each `concept. Limit the number of results
        based on the value of the `related_to_limit` parameter.
        */
        WITH concept,
             collect({{
                 concept_node: selected_related_concept,
                 source_concept_nodes: related_source_concepts,
                 edge: selected_related_edge,
                 concept_types: related_types
             }})[0..$related_to_limit] AS related
             
        /* Return the ID of each concept and a corresponding list of related concepts. */
        RETURN 
            concept.id AS id,
            related
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
        "work_edge_1.referenced_type", source_referenced_types
    )
    related_referenced_type_filter = _get_referenced_together_filter(
        "work_edge_2.referenced_type", related_referenced_types
    )
    source_referenced_in_filter = _get_referenced_together_filter(
        "work_edge_1.referenced_in", source_referenced_in
    )
    related_referenced_in_filter = _get_referenced_together_filter(
        "work_edge_2.referenced_in", related_referenced_in
    )
