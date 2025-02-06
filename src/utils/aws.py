import csv
import json
from collections.abc import Generator
from functools import lru_cache
from typing import Any

import boto3
import smart_open

import config
from clients.base_neptune_client import BaseNeptuneClient
from clients.lambda_neptune_client import LambdaNeptuneClient
from clients.local_neptune_client import LocalNeptuneClient
from utils.types import NodeType, OntologyType

LOAD_BALANCER_SECRET_NAME = "NeptuneTest/LoadBalancerUrl"
INSTANCE_ENDPOINT_SECRET_NAME = "NeptuneTest/InstanceEndpoint"


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


@lru_cache
def fetch_transformer_output_from_s3(
    ontology_type: OntologyType, node_type: NodeType
) -> Generator[list[Any]]:
    """Retrieves the bulk load file outputted by the relevant transformer so that we can extract data from it."""
    linked_nodes_file_name = f"{ontology_type}_{node_type}__nodes.csv"
    s3_url = f"s3://{config.S3_BULK_LOAD_BUCKET_NAME}/{linked_nodes_file_name}"

    transport_params = {"client": boto3.client("s3")}
    with smart_open.open(s3_url, "r", transport_params=transport_params) as f:
        csv_reader = csv.reader(f)

        for i, row in enumerate(csv_reader):
            # Skip header
            if i == 0:
                continue
            yield row
