"""
When the snapshot generator completes, it sends a SnapshotCompleted message
to an SNS topic.

This Lambda reads the messages from that SNS topic, and records them in
Elasticsearch.
"""

import json
import os

import boto3
from elasticsearch import Elasticsearch
import humanize


def get_sns_messages(event):
    """
    Extracts messages from an SNS event sent to a Lambda function.
    """
    try:
        event_records = event["Records"]
    except KeyError:
        pass

    for record in event_records:
        assert record["EventSource"] == "aws:sns", record

        yield json.loads(record["Sns"]["Message"])


def get_elastic_client(secret_id):
    """
    Use secrets from SecretsManager to construct an Elasticsearch client.
    """
    secrets_client = boto3.client("secretsmanager")

    resp = secrets_client.get_secret_value(SecretId=secret_id)

    # The secret response is a JSON string of the form
    # {"username": "…", "password": "…", "endpoint": "…"}
    secret = json.loads(resp["SecretString"])

    return Elasticsearch(
        secret["endpoint"], http_auth=(secret["username"], secret["password"])
    )


def prepare_message_for_indexing(completed_snapshot):
    """
    Add any extra fields to the CompletedSnapshotJob that we want to send
    to Elasticsearch.
    """
    # The snapshot generator returns the size of the final snapshot in bytes.
    # Include the snapshot size as a human-readable string (e.g. 100MB)
    # for humans to read in Kibana.
    completed_snapshot["snapshotResult"]["s3Size.humanReadable"] = humanize.naturalsize(
        completed_snapshot["snapshotResult"]["s3Size"]
    )


def main(event, _):
    index = "snapshots"

    secret_id = os.environ["SECRET_ID"]

    es_client = get_elastic_client(secret_id=secret_id)

    for completed_snapshot in get_sns_messages(event):
        prepare_message_for_indexing(completed_snapshot)

        es_client.index(index=index, body=completed_snapshot)
