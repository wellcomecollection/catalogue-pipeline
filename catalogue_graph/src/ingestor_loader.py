#!/usr/bin/env python

import argparse
import pprint
import time
import typing

import boto3
import polars as pl
import smart_open
from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from ingestor_indexer import IngestorIndexerLambdaEvent, IngestorIndexerObject
from models.catalogue_concept import (
    CatalogueConcept,
    ConceptsQueryResult,
    ConceptsQuerySingleResult,
    MissingLabelError,
)
from models.graph_node import ConceptType
from utils.aws import get_neptune_client
from utils.types import WorkConceptKey


class IngestorLoaderLambdaEvent(BaseModel):
    job_id: str
    pipeline_date: str | None
    index_date: str | None
    start_offset: int
    end_index: int


class IngestorLoaderConfig(BaseModel):
    loader_s3_bucket: str = INGESTOR_S3_BUCKET
    loader_s3_prefix: str = INGESTOR_S3_PREFIX
    is_local: bool = False


# Maximum number of related nodes to return for each relationship type
RELATED_TO_LIMIT = 10

# Minimum number of works in which two concepts must co-occur to be considered 'frequently referenced together'
NUMBER_OF_SHARED_WORKS_THRESHOLD = 3

# There are a few Wikidata supernodes which cause performance issues in queries.
# We need to filter them out when running queries to get related nodes.
# Q5 -> 'human', Q151885 -> 'concept'
IGNORED_WIKIDATA_IDS = ["Q5", "Q151885"]


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


def related_query_result_to_dict(related_to: list[dict]) -> dict[str, list[dict]]:
    """
    Transform a list of dictionaries mapping a concept ID to a list of related concepts into a single dictionary
    (with concept IDs as keys and related concepts as values).
    """
    return {item["id"]: item["related"] for item in related_to}


def extract_data(
    start_offset: int, end_index: int, is_local: bool
) -> ConceptsQueryResult:
    print("Extracting data from Neptune ...")
    client = get_neptune_client(is_local)

    limit = end_index - start_offset
    print(f"Processing records from {start_offset} to {end_index} ({limit} records)")

    field_of_work_query = get_related_query("HAS_FIELD_OF_WORK")
    related_to_query = get_related_query("RELATED_TO")
    narrower_than_query = get_related_query("NARROWER_THAN")
    broader_than_query = get_related_query("NARROWER_THAN|HAS_PARENT", "to")
    people_query = get_related_query("HAS_FIELD_OF_WORK", "to")

    referenced_together_query = get_referenced_together_query()

    # Retrieve people and organisations which are commonly referenced together as collaborators with a given person
    frequent_collaborators_query = get_referenced_together_query(
        source_referenced_types=["Person"],
        related_referenced_types=["Person", "Organisation"],
        source_referenced_in=["contributors"],
        related_referenced_in=["contributors"],
    )

    # Do not include agents/people/orgs in the list of related topics.
    related_topics_query = get_referenced_together_query(
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

    params = {
        "start_offset": start_offset,
        "limit": limit,
        "ignored_wikidata_ids": IGNORED_WIKIDATA_IDS,
        "related_to_limit": RELATED_TO_LIMIT,
        "number_of_shared_works_threshold": NUMBER_OF_SHARED_WORKS_THRESHOLD,
    }

    def time_query(query: str, label: str) -> list[dict]:
        t = time.time()
        result = client.run_open_cypher_query(query, params)
        print(
            f"Ran '{label}' query in {round(time.time() - t)} seconds, retrieving {len(result)} records."
        )
        return result

    concept_result = time_query(CONCEPT_QUERY, "concept")
    related_to_result = time_query(related_to_query, "related to")
    field_of_work_result = time_query(field_of_work_query, "field of work")
    narrower_than_result = time_query(narrower_than_query, "narrower than")
    broader_than_result = time_query(broader_than_query, "broader than")
    people_result = time_query(people_query, "people")
    referenced_together_result = time_query(
        referenced_together_query, "referenced together"
    )
    frequent_collaborators_result = time_query(
        frequent_collaborators_query, "frequent collaborators"
    )
    related_topics_result = time_query(related_topics_query, "related topics")

    return ConceptsQueryResult(
        concepts=concept_result,
        related_to=related_query_result_to_dict(related_to_result),
        fields_of_work=related_query_result_to_dict(field_of_work_result),
        narrower_than=related_query_result_to_dict(narrower_than_result),
        broader_than=related_query_result_to_dict(broader_than_result),
        people=related_query_result_to_dict(people_result),
        referenced_together=related_query_result_to_dict(referenced_together_result),
        frequent_collaborators=related_query_result_to_dict(
            frequent_collaborators_result
        ),
        related_topics=related_query_result_to_dict(related_topics_result),
    )


def transform_data(neptune_data: ConceptsQueryResult) -> list[CatalogueConcept]:
    print("Transforming data to CatalogueConcept ...")

    transformed = []
    for concept_data in neptune_data.concepts:
        concept_id = concept_data["concept"]["~properties"]["id"]

        result = ConceptsQuerySingleResult(
            concept=concept_data,
            related_to=neptune_data.related_to.get(concept_id, []),
            fields_of_work=neptune_data.fields_of_work.get(concept_id, []),
            narrower_than=neptune_data.narrower_than.get(concept_id, []),
            broader_than=neptune_data.broader_than.get(concept_id, []),
            people=neptune_data.people.get(concept_id, []),
            referenced_together=neptune_data.referenced_together.get(concept_id, []),
            frequent_collaborators=neptune_data.frequent_collaborators.get(
                concept_id, []
            ),
            related_topics=neptune_data.related_topics.get(concept_id, []),
        )

        try:
            catalogue_concept = CatalogueConcept.from_neptune_result(result)
            transformed.append(catalogue_concept)
        except MissingLabelError:
            # There is currently one concept which does not have a label ('k6p2u5fh')
            print(
                f"Concept {concept_id} does not have a label and will not be indexed."
            )

    return transformed


def load_data(s3_uri: str, data: list[CatalogueConcept]) -> IngestorIndexerObject:
    print(f"Loading data to {s3_uri} ...")

    # using polars write to parquet in S3 using smart_open
    transport_params = {"client": boto3.client("s3")}

    with smart_open.open(s3_uri, "wb", transport_params=transport_params) as f:
        df = pl.DataFrame([e.model_dump() for e in data])
        df.write_parquet(f)

    boto_s3_object = f.to_boto3(boto3.resource("s3"))
    content_length = boto_s3_object.content_length

    print(f"Data loaded to {s3_uri} with content length {content_length}")

    assert content_length is not None, "Content length should not be None"
    assert len(df) == len(data), "DataFrame length should match data length"

    return IngestorIndexerObject(
        s3_uri=s3_uri,
        content_length=content_length,
        record_count=len(df),
    )


def handler(
    event: IngestorLoaderLambdaEvent, config: IngestorLoaderConfig
) -> IngestorIndexerLambdaEvent:
    print(f"Received event: {event} with config {config}")

    pipeline_date = event.pipeline_date or "dev"
    index_date = event.index_date or "dev"

    filename = (
        f"{str(event.start_offset).zfill(8)}-{str(event.end_index).zfill(8)}.parquet"
    )
    s3_object_key = f"{pipeline_date}/{index_date}/{event.job_id}/{filename}"
    s3_uri = f"s3://{config.loader_s3_bucket}/{config.loader_s3_prefix}/{s3_object_key}"

    extracted_data = extract_data(
        start_offset=event.start_offset,
        end_index=event.end_index,
        is_local=config.is_local,
    )
    transformed_data = transform_data(extracted_data)
    result = load_data(s3_uri=s3_uri, data=transformed_data)

    print(f"Data loaded successfully: {result}")

    return IngestorIndexerLambdaEvent(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=event.job_id,
        object_to_index=result,
    )


def lambda_handler(event: IngestorLoaderLambdaEvent, context: typing.Any) -> dict:
    return handler(
        IngestorLoaderLambdaEvent.model_validate(event), IngestorLoaderConfig()
    ).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--start-offset",
        type=int,
        help="The start index of the records to process.",
        required=False,
        default=0,
    )
    parser.add_argument(
        "--end-index",
        type=int,
        help="The end index of the records to process.",
        required=False,
        default=100,
    )
    parser.add_argument(
        "--job-id",
        type=str,
        help='The job identifier used in the S3 path, will default to "dev".',
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--pipeline-date",
        type=str,
        help='The pipeline that is being ingested to, will default to "dev".',
        required=False,
        default="dev",
    )
    parser.add_argument(
        "--index-date",
        type=str,
        help='The concepts index date that is being ingested to, will default to "dev".',
        required=False,
        default="dev",
    )

    args = parser.parse_args()

    event = IngestorLoaderLambdaEvent(**args.__dict__)
    config = IngestorLoaderConfig(is_local=True)

    result = handler(event, config)

    pprint.pprint(result.model_dump())


if __name__ == "__main__":
    local_handler()
