#!/usr/bin/env python

import argparse
import pprint
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
)
from utils.aws import get_neptune_client


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

# There are a few Wikidata supernodes which cause performance issues in queries.
# We need to filter them out when running queries to get related nodes.
# Q5 -> 'human', Q151885 -> 'concept'
IGNORED_WIKIDATA_IDS = ["Q5", "Q151885"]


def get_related_query(
    edge_type: str,
    direction: str = "from",
    source_concept_label_types: list[str] | None = None,
) -> str:
    """
    Return a parameterized Neptune query to fetch related Wellcome concepts:
        1. For each Wellcome concept (`Concept` node), retrieve its associated `SourceConcept` nodes.
        2. For each `SourceConcept`, traverse edges of type `edge_type` in the specified `direction`
        (e.g. `edge_type="NARROWER_THAN"` combined with `direction="from"` would yield broader concepts)
        to find related `SourceConcept` nodes.
        3. Get the Wellcome concept(s) associated with each related `SourceConcept`, deduplicate with a `WITH`
        clause and return the most popular concepts (determined by the number of Works in which they appear).
    """
    label_filter = ""
    if source_concept_label_types is not None and len(source_concept_label_types) > 0:
        label_filter = "WHERE " + " OR ".join(
            [f"linked_source_concept:{c}" for c in source_concept_label_types]
        )

    left_arrow = "<" if direction == "to" else ""
    right_arrow = ">" if direction == "from" else ""

    return f"""
        MATCH (concept:Concept)
        WITH concept ORDER BY concept.id
        SKIP $start_offset LIMIT $limit
        MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)-[:SAME_AS*0..2]->(source_concept)
        WHERE NOT source_concept.id IN $ignored_wikidata_ids
        MATCH (source_concept){left_arrow}-[rel:{edge_type}]-{right_arrow}(linked_related_source_concept)
        MATCH (linked_related_source_concept)-[:SAME_AS*0..2]->(related_source_concept)
        WHERE NOT linked_related_source_concept.id IN $ignored_wikidata_ids
            AND NOT related_source_concept.id IN $ignored_wikidata_ids
            AND NOT (linked_source_concept)-[:SAME_AS*0..2]-(related_source_concept)        
        MATCH (related_source_concept)<-[:HAS_SOURCE_CONCEPT]-(related_concept)
        MATCH (work)-[:HAS_CONCEPT]->(related_concept)
        {label_filter}
        WITH concept,
             linked_related_source_concept,
             COUNT(work) AS number_of_works,
             collect(DISTINCT related_source_concept) AS related_source_concepts,
             head(collect(related_concept)) AS selected_related_concept,
             head(collect(rel)) AS selected_related_edge
        ORDER BY number_of_works DESC
        WITH concept,
             collect({{
                 concept_node: selected_related_concept,
                 source_concept_nodes: related_source_concepts,
                 edge: selected_related_edge
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
    RETURN 
        concept,
        collect(DISTINCT linked_source_concept) AS linked_source_concepts,
        collect(DISTINCT source_concept) AS source_concepts,
        collect(DISTINCT same_as_concept.id) AS same_as_concept_ids        
    """

# For every Wellcome concept, this query returns a list of concepts most frequently co‑occurring with it in Works.
REFERENCED_TOGETHER_QUERY = """
        MATCH (concept:Concept)
        WITH concept ORDER BY concept.id
        SKIP $start_offset LIMIT $limit
        MATCH (concept)<-[:HAS_CONCEPT]-(w:Work)-[:HAS_CONCEPT]->(other:Concept),
              (other)-[:HAS_SOURCE_CONCEPT]->(linked_other_source_concept)-[:SAME_AS*0..2]-(other_source_concept)
        WHERE NOT other_source_concept.id IN $ignored_wikidata_ids
        WITH DISTINCT concept, linked_other_source_concept, other, other_source_concept, count(w) AS number_of_works
        WITH concept,
             linked_other_source_concept,
             head(collect(other)) AS selected_other,
             collect(other_source_concept) AS related_source_concepts,
             SUM(number_of_works) AS number_of_works
        ORDER BY number_of_works DESC
        WHERE number_of_works >= 10
        WITH concept,
            collect({
                concept_node: selected_other,
                source_concept_nodes: related_source_concepts
            })[0..$related_to_limit] AS related        
        RETURN
            concept.id AS id,
            related
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

    params = {
        "start_offset": start_offset,
        "limit": limit,
        "ignored_wikidata_ids": IGNORED_WIKIDATA_IDS,
        "related_to_limit": RELATED_TO_LIMIT,
    }

    print("Running concept query...")
    concept_result = client.run_open_cypher_query(CONCEPT_QUERY, params)
    print(f"Retrieved {len(concept_result)} records")

    print("Running related to query...")
    related_to_result = client.run_open_cypher_query(related_to_query, params)
    print(f"Retrieved {len(related_to_result)} records")

    print("Running field of work query...")
    field_of_work_result = client.run_open_cypher_query(field_of_work_query, params)
    print(f"Retrieved {len(field_of_work_result)} records")

    print("Running narrower than query...")
    narrower_than_result = client.run_open_cypher_query(narrower_than_query, params)
    print(f"Retrieved {len(narrower_than_result)} records")

    print("Running broader than query...")
    broader_than_result = client.run_open_cypher_query(broader_than_query, params)
    print(f"Retrieved {len(broader_than_result)} records")

    print("Running people query...")
    people_result = client.run_open_cypher_query(people_query, params)
    print(f"Retrieved {len(people_result)} records")

    print("Running referenced together query...")
    referenced_together_result = client.run_open_cypher_query(
        REFERENCED_TOGETHER_QUERY, params
    )
    print(f"Retrieved {len(referenced_together_result)} records")

    return ConceptsQueryResult(
        concepts=concept_result,
        related_to=related_query_result_to_dict(related_to_result),
        fields_of_work=related_query_result_to_dict(field_of_work_result),
        narrower_than=related_query_result_to_dict(narrower_than_result),
        broader_than=related_query_result_to_dict(broader_than_result),
        people=related_query_result_to_dict(people_result),
        referenced_together=related_query_result_to_dict(referenced_together_result),
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
        )
        transformed.append(CatalogueConcept.from_neptune_result(result))

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
