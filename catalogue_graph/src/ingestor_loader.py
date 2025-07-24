#!/usr/bin/env python

import argparse
import pprint
from collections.abc import Generator
from typing import Any

import boto3
import polars as pl
import smart_open
from pydantic import BaseModel

from config import INGESTOR_S3_BUCKET, INGESTOR_S3_PREFIX
from elasticsearch_transformers.concepts_transformer import (
    ElasticsearchConceptsTransformer,
)
from elasticsearch_transformers.raw_neptune_concept import MissingLabelError
from ingestor_indexer import IngestorIndexerLambdaEvent, IngestorIndexerObject
from models.indexable_concept import (
    IndexableConcept,
)
from queries.concept_queries import (
    get_broader_concepts,
    get_collaborator_concepts,
    get_concepts,
    get_field_of_work_concepts,
    get_narrower_concepts,
    get_people_concepts,
    get_related_concepts,
    get_related_topics,
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


def _get_params(start_offset: int, limit: int) -> dict:
    return {
        "start_offset": start_offset,
        "limit": limit,
        "ignored_wikidata_ids": IGNORED_WIKIDATA_IDS,
        "related_to_limit": RELATED_TO_LIMIT,
        "number_of_shared_works_threshold": NUMBER_OF_SHARED_WORKS_THRESHOLD,
    }


def extract_data(
    start_offset: int, end_index: int, is_local: bool
) -> Generator[IndexableConcept]:
    limit = end_index - start_offset
    print(f"Processing records from {start_offset} to {end_index} ({limit} records)")

    client = get_neptune_client(is_local)
    params = _get_params(start_offset, limit)

    concepts = get_concepts(client, params)
    related_concepts = {
        "related_to": get_related_concepts(client, params),
        "fields_of_work": get_field_of_work_concepts(client, params),
        "narrower_than": get_narrower_concepts(client, params),
        "broader_than": get_broader_concepts(client, params),
        "people": get_people_concepts(client, params),
        "frequent_collaborators": get_collaborator_concepts(client, params),
        "related_topics": get_related_topics(client, params),
    }

    transformer = ElasticsearchConceptsTransformer()
    for concept in concepts:
        try:
            yield transformer.transform_document(concept, related_concepts)
        except MissingLabelError:
            # There is currently one concept which does not have a label ('k6p2u5fh')
            concept_id = concept["concept"]["~properties"]["id"]
            print(
                f"Concept {concept_id} does not have a label and will not be indexed."
            )


def load_data(s3_uri: str, data: list[IndexableConcept]) -> IngestorIndexerObject:
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
    result = load_data(s3_uri=s3_uri, data=list(extracted_data))

    print(f"Data loaded successfully: {result}")

    return IngestorIndexerLambdaEvent(
        pipeline_date=pipeline_date,
        index_date=index_date,
        job_id=event.job_id,
        object_to_index=result,
    )


def lambda_handler(event: IngestorLoaderLambdaEvent, context: Any) -> dict:
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
