#!/usr/bin/env python

import argparse
import pprint
import typing

import boto3
import polars as pl
import smart_open
from clients.base_neptune_client import BaseNeptuneClient
from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from ingestor_indexer import IngestorIndexerLambdaEvent, IngestorIndexerObject
from models.catalogue_concept import (
    CatalogueConcept,
    ConceptsQueryResult,
    ConceptsQuerySingleResult,
)
from pydantic import BaseModel
from queries.concept import (
    CONCEPT_QUERY,
    get_referenced_together_query,
    get_related_query,
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

# Minimum number of works in which two concepts must co-occur to be considered 'frequently referenced together'
NUMBER_OF_SHARED_WORKS_THRESHOLD = 3

# There are a few Wikidata supernodes which cause performance issues in queries.
# We need to filter them out when running queries to get related nodes.
# Q5 -> 'human', Q151885 -> 'concept'
IGNORED_WIKIDATA_IDS = ["Q5", "Q151885"]

LinkedConcepts = dict[str, list[dict]]


def _related_query_result_to_dict(related_to: list[dict]) -> LinkedConcepts:
    """
    Transform a list of dictionaries mapping a concept ID to a list of related concepts into a single dictionary
    (with concept IDs as keys and related concepts as values).
    """
    return {item["id"]: item["related"] for item in related_to}


def related_concepts(client: BaseNeptuneClient, params: dict) -> LinkedConcepts:
    query = get_related_query("RELATED_TO")
    result = client.time_open_cypher_query(query, params, "related to")
    return _related_query_result_to_dict(result)


def field_of_work_concepts(client: BaseNeptuneClient, params: dict) -> LinkedConcepts:
    query = get_related_query("HAS_FIELD_OF_WORK")
    result = client.time_open_cypher_query(query, params, "field of work")
    return _related_query_result_to_dict(result)


def narrower_concepts(client: BaseNeptuneClient, params: dict) -> LinkedConcepts:
    query = get_related_query("NARROWER_THAN")
    result = client.time_open_cypher_query(query, params, "narrower than")
    return _related_query_result_to_dict(result)


def broader_concepts(client: BaseNeptuneClient, params: dict) -> LinkedConcepts:
    query = get_related_query("NARROWER_THAN|HAS_PARENT", "to")
    result = client.time_open_cypher_query(query, params, "broader than")
    return _related_query_result_to_dict(result)


def people_concepts(client: BaseNeptuneClient, params: dict) -> LinkedConcepts:
    query = get_related_query("HAS_FIELD_OF_WORK", "to")
    result = client.time_open_cypher_query(query, params, "people")
    return _related_query_result_to_dict(result)


def coreferenced_concepts(client: BaseNeptuneClient, params: dict) -> LinkedConcepts:
    query = get_referenced_together_query()
    result = client.time_open_cypher_query(query, params, "referenced together")
    return _related_query_result_to_dict(result)


def collaborator_concepts(client: BaseNeptuneClient, params: dict) -> LinkedConcepts:
    # Retrieve people and organisations which are commonly referenced together as collaborators with a given person
    query = get_referenced_together_query(
        source_referenced_types=["Person"],
        related_referenced_types=["Person", "Organisation"],
        source_referenced_in=["contributors"],
        related_referenced_in=["contributors"],
    )
    result = client.time_open_cypher_query(query, params, "frequent collaborators")
    return _related_query_result_to_dict(result)


def related_topics(client: BaseNeptuneClient, params: dict) -> LinkedConcepts:
    # Do not include agents/people/orgs in the list of related topics.
    query = get_referenced_together_query(
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
    result = client.time_open_cypher_query(query, params, "related topics")
    return _related_query_result_to_dict(result)


def extract_data(
    start_offset: int, end_index: int, is_local: bool
) -> ConceptsQueryResult:
    client = get_neptune_client(is_local)

    limit = end_index - start_offset
    print(f"Processing records from {start_offset} to {end_index} ({limit} records)")

    params = {
        "start_offset": start_offset,
        "limit": limit,
        "ignored_wikidata_ids": IGNORED_WIKIDATA_IDS,
        "related_to_limit": RELATED_TO_LIMIT,
        "number_of_shared_works_threshold": NUMBER_OF_SHARED_WORKS_THRESHOLD,
    }

    concept_result = client.time_open_cypher_query(CONCEPT_QUERY, params, "concept")

    return ConceptsQueryResult(
        concepts=concept_result,
        related_to=related_concepts(client, params),
        fields_of_work=field_of_work_concepts(client, params),
        narrower_than=narrower_concepts(client, params),
        broader_than=broader_concepts(client, params),
        people=people_concepts(client, params),
        referenced_together=coreferenced_concepts(client, params),
        frequent_collaborators=collaborator_concepts(client, params),
        related_topics=related_topics(client, params),
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
