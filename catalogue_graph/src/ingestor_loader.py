#!/usr/bin/env python

import argparse
import pprint
import typing

import boto3
import polars as pl
import smart_open
from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from ingestor_indexer import IngestorIndexerLambdaEvent, IngestorIndexerObject
from models.catalogue_concept import CatalogueConcept
from pydantic import BaseModel
from utils.aws import get_neptune_client


class QueryResult(BaseModel):
    concepts: list[dict]
    related_to: list[dict]
    fields_of_work: list[dict]
    narrower_than: list[dict]

class IngestorLoaderLambdaEvent(BaseModel):
    job_id: str
    pipeline_date: str | None
    start_offset: int
    end_index: int


class IngestorLoaderConfig(BaseModel):
    loader_s3_bucket: str = INGESTOR_S3_BUCKET
    loader_s3_prefix: str = INGESTOR_S3_PREFIX
    is_local: bool = False


def get_related_query(edge_type: str, start_offset: int, limit: int, source_concept_label_types: list[str] | None = None) -> str:
    label_filter = ""
    if source_concept_label_types is not None and len(source_concept_label_types) > 0:
        label_filter = "WHERE " + " OR ".join([f"linked_source_concept:{c}" for c in source_concept_label_types])

    return f"""
        MATCH (concept:Concept)
        WITH concept ORDER BY concept.id
        SKIP {start_offset} LIMIT {limit}
        MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)-[:SAME_AS*0..]->(source_concept)
        MATCH (source_concept)-[rel:{edge_type}]->(linked_related_source_concept)
        MATCH (linked_related_source_concept)-[:SAME_AS*0..]->(related_source_concept)
        MATCH (related_source_concept)<-[:HAS_SOURCE_CONCEPT]-(related_concept)
        {label_filter}
        WITH concept, 
             linked_related_source_concept,
             collect(DISTINCT related_source_concept) AS related_source_concepts,
             head(collect(related_concept)) AS selected_related_concept,
             head(collect(rel)) AS selected_related_edge      
        WITH concept,
             collect({{
                 concept_node: selected_related_concept,
                 source_concept_nodes: related_source_concepts,
                 edge: selected_related_edge
             }}) AS related             
        RETURN 
            concept.id AS id,
            related
    """


def extract_data(start_offset: int, end_index: int, is_local: bool) -> QueryResult:
    print("Extracting data from Neptune ...")
    client = get_neptune_client(is_local)

    limit = end_index - start_offset
    print(f"Processing records from {start_offset} to {end_index} ({limit} records)")

    open_cypher_concept_query = f"""
    MATCH (concept:Concept)
    WITH concept ORDER BY concept.id
    SKIP {start_offset} LIMIT {limit}
    OPTIONAL MATCH (concept)-[:HAS_SOURCE_CONCEPT]->(linked_source_concept)-[:SAME_AS*0..]->(source_concept)
    OPTIONAL MATCH (source_concept)<-[:HAS_SOURCE_CONCEPT]-(same_as_concept)
    RETURN 
        concept,
        collect(DISTINCT linked_source_concept) AS linked_source_concepts,
        collect(DISTINCT source_concept) AS source_concepts,
        collect(DISTINCT same_as_concept.id) AS same_as_concept_ids        
    """

    field_of_work_query = get_related_query("HAS_FIELD_OF_WORK", start_offset, limit)
    related_to_query = get_related_query("RELATED_TO", start_offset, limit)

    # Apply label filter to filter out 'Human' matches
    narrower_than_query = get_related_query("NARROWER_THAN", start_offset, limit, ["SourceConcept", "SourceLocation"])

    print("Running concept query...")
    concept_result = client.run_open_cypher_query(open_cypher_concept_query)
    print(f"Retrieved {len(concept_result)} records")

    print("Running related to query...")
    related_to_result = client.run_open_cypher_query(related_to_query)
    print(f"Retrieved {len(related_to_result)} records")

    print("Running field of work query...")
    field_of_work_result = client.run_open_cypher_query(field_of_work_query)
    print(f"Retrieved {len(field_of_work_result)} records")

    print("Running narrower than query...")
    narrower_than_result = client.run_open_cypher_query(narrower_than_query)
    print(f"Retrieved {len(narrower_than_result)} records")

    return QueryResult(
        concepts=concept_result,
        related_to=related_to_result,
        fields_of_work=field_of_work_result,
        narrower_than=narrower_than_result

    )


def transform_data(neptune_data: QueryResult) -> list[CatalogueConcept]:
    print("Transforming data to CatalogueConcept ...")    
    related_to = {item['id']: item['related'] for item in neptune_data.related_to}
    fields_of_work = {item['id']: item['related'] for item in neptune_data.fields_of_work}
    narrower_than = {item['id']: item['related'] for item in neptune_data.narrower_than}

    return [
        CatalogueConcept.from_neptune_result(
            concept,
            related_to.get(concept["concept"]["~properties"]["id"], []),
            fields_of_work.get(concept["concept"]["~properties"]["id"], []),
            narrower_than.get(concept["concept"]["~properties"]["id"], [])
        ) for concept in neptune_data.concepts
    ]


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
    filename = (
        f"{str(event.start_offset).zfill(8)}-{str(event.end_index).zfill(8)}.parquet"
    )
    s3_object_key = f"{event.pipeline_date or 'dev'}/{event.job_id}/{filename}"
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
        pipeline_date=event.pipeline_date,
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

    args = parser.parse_args()

    event = IngestorLoaderLambdaEvent(**args.__dict__)
    config = IngestorLoaderConfig(is_local=True)

    result = handler(event, config)

    pprint.pprint(result.model_dump())


if __name__ == "__main__":
    local_handler()
