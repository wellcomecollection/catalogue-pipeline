"""
What do?

"""

import boto3
from elasticsearch import Elasticsearch
import httpx
import humanize

from dateutil import parser
import json
import os


def get_secret(secret_id):
    """
    Use secrets from SecretsManager to construct an Elasticsearch client.
    """
    secrets_client = boto3.client("secretsmanager")

    resp = secrets_client.get_secret_value(SecretId=secret_id)

    try:
        # The secret response may be a JSON string of the form
        # {"username": "…", "password": "…", "endpoint": "…"}
        secret = json.loads(resp["SecretString"])
    except ValueError:
        secret = resp["SecretString"]

    return secret


def get_elastic_client(elastic_secret_id):
    secret = get_secret(elastic_secret_id)

    return Elasticsearch(
        secret["endpoint"], http_auth=(secret["username"], secret["password"])
    )


def get_snapshots(es_client, days_to_fetch):
    index = "snapshots"

    date_filter = [
        {"range": {"snapshotJob.requestedAt": {"gte": f"now-{days_to_fetch}d/d"}}}
    ]
    es_query = {"bool": {"filter": date_filter}}
    sort_by = [{"snapshotJob.requestedAt": {"order": "desc"}}]

    response = es_client.search(index=index, body={"query": es_query, "sort": sort_by})

    return [hit["_source"] for hit in response["hits"]["hits"]]


def prepare_slack_payload(snapshots, days_to_fetch):
    def _snapshot_message(snapshot):
        index_name = snapshot["snapshotResult"]["indexName"]
        document_count = snapshot["snapshotResult"]["documentCount"]
        s3_size = snapshot["snapshotResult"]["s3Size"]["bytes"]

        requested_at = parser.parse(snapshot["snapshotJob"]["requestedAt"]).strftime(
            "%A, %B %-d, %I:%M %p"
        )

        started_at = parser.parse(snapshot["snapshotResult"]["startedAt"])

        finished_at = parser.parse(snapshot["snapshotResult"]["finishedAt"])

        return "\n".join(
            [
                f'*"{index_name}" on {requested_at}*',
                f"{humanize.naturalsize(s3_size)} containing {humanize.intword(document_count)} documents ({humanize.intcomma(document_count)} exactly)",
                f"Took {humanize.precisedelta(finished_at - started_at)}",
            ]
        )

    heading = f"Snapshots in the last {days_to_fetch} days"

    heading_block = [
        {"type": "header", "text": {"type": "plain_text", "text": heading}}
    ]

    snapshot_blocks = []
    for snapshot in snapshots:
        snapshot_blocks.append(
            {
                "type": "section",
                "text": {"type": "mrkdwn", "text": _snapshot_message(snapshot)},
            }
        )

    result = {"blocks": heading_block + snapshot_blocks}

    return result


def post_to_slack(slack_secret_id, payload):
    resp = httpx.post(get_secret(slack_secret_id), json=payload)

    print(f"Sent payload to Slack: {resp}")

    if resp.status_code != 200:
        print("Non-200 response from Slack:")

        print("")

        print("== request ==")
        print(json.dumps(payload, indent=2, sort_keys=True))

        print("")

        print("== response ==")
        print(resp.text)


def main(*args):
    elastic_secret_id = os.environ["ELASTIC_SECRET_ID"]
    slack_secret_id = os.environ["SLACK_SECRET_ID"]

    elastic_client = get_elastic_client(elastic_secret_id)

    days_to_fetch = 1

    snapshots = get_snapshots(elastic_client, days_to_fetch)
    slack_payload = prepare_slack_payload(snapshots, days_to_fetch)

    post_to_slack(slack_secret_id, slack_payload)


if __name__ == "__main__":
    main()
