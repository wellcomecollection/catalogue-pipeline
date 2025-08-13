from utils.types import ConceptType, WorkConceptKey

LinkedConcepts = dict[str, list[dict]]

# A query returning all Wellcome concepts and the corresponding `SourceConcepts`.
CONCEPT_QUERY = """
    MATCH (concept:Concept)
    WITH concept ORDER BY concept.id
    SKIP $start_offset LIMIT $limit
    OPTIONAL MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)-[:SAME_AS*0..]->(source_concept)
    OPTIONAL MATCH (source_concept)<-[:HAS_SOURCE_CONCEPT]-(same_as_concept)
    OPTIONAL MATCH (work)-[has_concept:HAS_CONCEPT]-(concept)
    RETURN 
        concept,
        collect(DISTINCT linked_source_concept) AS linked_source_concepts,
        collect(DISTINCT source_concept) AS source_concepts,
        collect(DISTINCT same_as_concept.id) AS same_as_concept_ids,
        collect(DISTINCT has_concept.referenced_type) AS concept_types        
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
        /* Get a chunk of `Concept` nodes (Wellcome concepts) of size `limit` */
        MATCH (concept:Concept)
        WITH concept ORDER BY concept.id
        SKIP $start_offset LIMIT $limit
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

    return f"""
        /* Get a chunk of `Concept` nodes of size `limit` */
        MATCH (concept:Concept)
        WITH concept ORDER BY concept.id 
        SKIP $start_offset LIMIT $limit
    
        /* 
        For each `concept`, retrieve all identical ('same as') concepts by traversing its source concepts
        */
        OPTIONAL MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)-[:SAME_AS*0..2]->(source_concept)
        WHERE NOT source_concept.id IN $ignored_wikidata_ids
        OPTIONAL MATCH (source_concept)<-[:HAS_SOURCE_CONCEPT]-(same_as_concept)  
        
        /* 
        Deduplicate and coalesce `same_as_concept` with `concept` to handle label-derived concepts not connected to 
        a source concept.
        */
        WITH DISTINCT
            concept,
            linked_source_concept,
            coalesce(same_as_concept, concept) AS same_as_concept
    
        /*
        Next, for each `same_as_concept`, get all co-occurring concepts `other` (i.e. find all combinations of `other`
        and `same_as_concept` for which there is at least one work listing both `other` and `same_as_concept`).
        */
        MATCH (same_as_concept)<-[work_edge_1:HAS_CONCEPT]-(w:Work)-[work_edge_2:HAS_CONCEPT]->(other)
        WHERE same_as_concept.id <> other.id
        
        {source_referenced_type_filter}
        {related_referenced_type_filter}
        {source_referenced_in_filter}
        {related_referenced_in_filter}
        
        /*
        For each `other` concept, count the number of works in which it co-occurs with each `same_as_concept`, 
        and link the results back to the original `concept` (discarding `same_as_concept` nodes).
        (Note: We could do this in one step using `COUNT(DISTINCT w)`, but this would incur a significant performance
        penalty.)
        */
        WITH DISTINCT
            concept,
            linked_source_concept,
            other,
            w.id AS work_id,
            work_edge_2.referenced_type as other_type
        WITH
            concept,
            linked_source_concept,
            other,
            COUNT(work_id) AS number_of_shared_works,
            other_type
        
        /*
        Filter out `other` concepts which do not meet the minimum threshold for the number of shared works.
        */        
        WHERE number_of_shared_works >= $number_of_shared_works_threshold
        AND concept.id <> other.id
                        
        /* Match each `other` concept with all of its source concepts. */
        OPTIONAL MATCH (other)-[:HAS_SOURCE_CONCEPT]->(linked_other_source_concept)-[:SAME_AS*0..2]->(other_source_concept)
            WHERE NOT other_source_concept.id IN $ignored_wikidata_ids
            AND NOT (linked_source_concept)-[:SAME_AS*0..2]->(linked_other_source_concept)
        
        /*
        We need to distinguish between cases where `linked_other_source_concept` is null because `other` is
        a label-derived concept (in which case we should proceed and return it), and cases where it's null because it
        was filtered out by the WHERE clause above (in which case we should not).
        */ 
        WITH *
        WHERE size((other)-[:HAS_SOURCE_CONCEPT]->()) = 0 OR linked_other_source_concept IS NOT NULL
        
        /*
        Group the results into buckets, with one bucket for each combination of concept and co-occurring source concept.
        (Note that we do not create groups based on each `other` concept, as that would cause duplicates in cases
        where two `other` concepts have the same source concept.)
        */
        WITH concept,
             coalesce(linked_other_source_concept, other) AS linked_other_source_concept,
             head(collect(other)) AS selected_other,
             other_type,
             collect(other_source_concept) AS related_source_concepts,
             number_of_shared_works
        ORDER BY number_of_shared_works DESC
        
        /* Collect distinct types of each `other` concept */
        WITH concept,
             linked_other_source_concept,
             selected_other,
             collect(other_type) AS other_types,
             related_source_concepts,
             number_of_shared_works        
        
        /*
        Group the results again to ensure that only one row is returned for each `concept`. Limit the number of results
        based on the value of the `related_to_limit` parameter.
        */
        WITH concept,
            collect({{
                concept_node: selected_other,
                source_concept_nodes: related_source_concepts,
                number_of_shared_works: number_of_shared_works,
                concept_types: other_types 
            }})[0..$related_to_limit] AS related        
        RETURN
            concept.id AS id,
            related
        """
