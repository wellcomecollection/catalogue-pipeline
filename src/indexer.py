import boto3
import json

from clients.neptune_client import NeptuneClient


def _get_secret(secret_name: str):
    secrets_manager_client = boto3.client("secretsmanager", region_name="eu-west-1")
    response = secrets_manager_client.get_secret_value(SecretId=secret_name)

    return response["SecretString"]


def extract_sns_messages_from_sqs_event(event):
    queries = []

    for record in event["Records"]:
        query = json.loads(record["body"])["Message"]
        queries.append(query)

    return queries


def lambda_handler(event: dict, context):
    queries = extract_sns_messages_from_sqs_event(event)
    neptune_client = NeptuneClient(_get_secret("NeptuneTest/InstanceEndpoint"))

    for query in queries:
        neptune_client.run_open_cypher_query(query)


if __name__ == "__main__":
    lambda_handler({}, None)
