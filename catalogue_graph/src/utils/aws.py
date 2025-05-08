import csv
import json
from collections.abc import Generator
from typing import Any, TypeVar

import boto3
import polars as pl
import smart_open
from pydantic import BaseModel

import config
from clients.base_neptune_client import BaseNeptuneClient
from clients.lambda_neptune_client import LambdaNeptuneClient
from clients.local_neptune_client import LocalNeptuneClient
from utils.types import NodeType, OntologyType

PydanticModelType = TypeVar("PydanticModelType", bound=BaseModel)


LOAD_BALANCER_SECRET_NAME = "catalogue-graph/neptune-nlb-url"
INSTANCE_ENDPOINT_SECRET_NAME = "catalogue-graph/neptune-cluster-endpoint"
VALID_SOURCE_FILES = [
    ("concepts", "mesh"),
    ("concepts", "loc"),
    ("locations", "mesh"),
    ("locations", "loc"),
    ("names", "loc"),
    ("concepts", "wikidata_linked_mesh"),
    ("concepts", "wikidata_linked_loc"),
    ("locations", "wikidata_linked_mesh"),
    ("locations", "wikidata_linked_loc"),
    ("names", "wikidata_linked_loc"),
]


def get_secret(secret_name: str) -> str:
    """Returns an AWS Secrets Manager secret string associated with a given secret name."""
    secrets_manager_client = boto3.Session().client("secretsmanager")
    response = secrets_manager_client.get_secret_value(SecretId=secret_name)

    secret: str = response["SecretString"]
    return secret


def publish_batch_to_sns(topic_arn: str, messages: list[str]) -> None:
    """Publishes a batch of up to 10 messages to the specified SNS topic."""

    assert len(messages) <= 10

    request_entries = []
    for i, message in enumerate(messages):
        request_entries.append(
            {
                "Id": f"batch_message_{i}",
                "Message": json.dumps({"default": message}),
                "MessageStructure": "json",
            }
        )

    boto3.Session().client("sns").publish_batch(
        TopicArn=topic_arn,
        PublishBatchRequestEntries=request_entries,
    )


def get_neptune_client(is_local: bool) -> BaseNeptuneClient:
    """
    Returns an instance of LambdaNeptuneClient or LocalNeptuneClient (if `is_local` is True). LocalNeptuneClient
    should only be used when connecting to the cluster from outside the VPC.
    """
    if is_local:
        return LocalNeptuneClient(
            get_secret(LOAD_BALANCER_SECRET_NAME),
            get_secret(INSTANCE_ENDPOINT_SECRET_NAME),
        )
    else:
        return LambdaNeptuneClient(get_secret(INSTANCE_ENDPOINT_SECRET_NAME))


def get_csv_from_s3(s3_uri: str) -> Generator[Any]:
    transport_params = {"client": boto3.client("s3")}
    with smart_open.open(s3_uri, "r", transport_params=transport_params) as f:
        csv_reader = csv.DictReader(f)

        yield from csv_reader


def write_csv_to_s3(s3_uri: str, items: list[dict]) -> None:
    if not items:
        raise ValueError("Cannot create a CSV file from an empty list.")

    transport_params = {"client": boto3.client("s3")}
    with smart_open.open(s3_uri, "w", transport_params=transport_params) as f:
        csv_writer = csv.DictWriter(f, fieldnames=items[0].keys())
        csv_writer.writeheader()

        for item in items:
            csv_writer.writerow(item)


def fetch_transformer_output_from_s3(
    node_type: NodeType, source: OntologyType
) -> Generator[Any]:
    """Retrieves the bulk load file outputted by the relevant transformer so that we can extract data from it."""
    if (node_type, source) not in VALID_SOURCE_FILES:
        print(
            f"Invalid source and node_type combination: ({source}, {node_type}). Returning an empty generator."
        )
        return

    linked_nodes_file_name = f"{source}_{node_type}__nodes.csv"
    s3_uri = f"s3://{config.S3_BULK_LOAD_BUCKET_NAME}/{linked_nodes_file_name}"

    print(f"Retrieving ids of type '{node_type}' from ontology '{source}' from S3.")
    yield from get_csv_from_s3(s3_uri)


def df_from_s3_parquet(s3_file_uri: str) -> pl.DataFrame:
    transport_params = {"client": boto3.client("s3")}
    with smart_open.open(s3_file_uri, "rb", transport_params=transport_params) as f:
        df = pl.read_parquet(f)

    return df


def df_to_s3_parquet(df: pl.DataFrame, s3_file_uri: str) -> None:
    transport_params = {"client": boto3.client("s3")}
    with smart_open.open(s3_file_uri, "wb", transport_params=transport_params) as f:
        df.write_parquet(f)


def pydantic_to_s3_json(model: BaseModel, s3_uri: str) -> None:
    """Create a JSON file from a Pydantic model and save it to S3."""
    transport_params = {"client": boto3.client("s3")}
    with smart_open.open(s3_uri, "w", transport_params=transport_params) as f:
        f.write(model.model_dump_json())


def pydantic_from_s3_json(
    model_type: type[PydanticModelType], s3_uri: str, ignore_missing: bool = False
) -> PydanticModelType | None:
    """Create a Pydantic model of type `model_type` from a JSON file stored in S3."""
    try:
        with smart_open.open(s3_uri, "r") as f:
            return model_type.model_validate_json(f.read())
    except (OSError, KeyError) as e:
        # if file does not exist, ignore
        if ignore_missing:
            print(f"S3 file not found: {e}")
            return None

        raise
