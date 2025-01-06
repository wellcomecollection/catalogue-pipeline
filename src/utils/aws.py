import json

import boto3

from clients.base_neptune_client import BaseNeptuneClient
from clients.lambda_neptune_client import LambdaNeptuneClient
from clients.local_neptune_client import LocalNeptuneClient


def get_secret(secret_name: str) -> str:
    secrets_manager_client = boto3.client("secretsmanager", region_name="eu-west-1")
    response = secrets_manager_client.get_secret_value(SecretId=secret_name)

    secret: str = response["SecretString"]
    return secret


def publish_batch_to_sns(topic_arn: str, queries: list[str]) -> None:
    request_entries = []
    for i, query in enumerate(queries):
        request_entries.append(
            {
                "Id": f"batch_message_{i}",
                "Message": json.dumps({"default": query}),
                "MessageStructure": "json",
            }
        )

    boto3.client("sns").publish_batch(
        TopicArn=topic_arn,
        PublishBatchRequestEntries=request_entries,
    )


def get_neptune_client(is_local: bool) -> BaseNeptuneClient:
    if is_local:
        return LocalNeptuneClient(
            get_secret("NeptuneTest/LoadBalancerUrl"),
            get_secret("NeptuneTest/InstanceEndpoint"),
        )
    else:
        return LambdaNeptuneClient(get_secret("NeptuneTest/InstanceEndpoint"))
