"""
This lambda queries Elasticsearch for catalogue snapshots made within the last
day. The index queried is available on the reporting cluster.

This lambda should be triggered by a daily CloudWatch event.
"""

import datetime

import boto3
from elasticsearch import Elasticsearch
import httpx
import humanize
import pytz

from dateutil import parser
import json
import os


def get_secret(secret_id):
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


def get_snapshots(es_client, elastic_index):
    response = es_client.search(
        index=elastic_index,
        body={
            "query": {
                "bool": {
                    "filter": [
                        {"range": {"snapshotJob.requestedAt": {"gte": f"now-1d/d"}}}
                    ]
                }
            },
            "sort": [{"snapshotJob.requestedAt": {"order": "desc"}}],
        },
    )

    return [hit["_source"] for hit in response["hits"]["hits"]]


def get_catalogue_api_document_count(endpoint):
    """
    How many documents are available in the catalogue API?
    """
    resp = httpx.get(f"https://api.wellcomecollection.org/catalogue/v2/{endpoint}")
    return resp.json()["totalResults"]


def format_date(d):
    # The timestamps passed around by the snapshots pipeline are all UTC.
    # This Lambda reports into our Slack channel, so adjust the time if
    # necessary for the UK, and add the appropriate timezone label.
    d = d.astimezone(pytz.timezone("Europe/London"))

    if d.date() == datetime.datetime.now().date():
        return d.strftime("today at %-I:%M %p %Z")
    elif d.date() == (datetime.datetime.now() - datetime.timedelta(days=1)).date():
        return d.strftime("yesterday at %-I:%M %p %Z")
    else:
        return d.strftime("on %A, %B %-d at %-I:%M %p %Z")


def prepare_slack_payload(snapshots, api_document_count):
    def _snapshot_message(snapshot):
        index_name = snapshot["snapshotResult"]["indexName"]
        snapshot_document_count = snapshot["snapshotResult"]["documentCount"]
        s3_size = snapshot["snapshotResult"]["s3Size"]["bytes"]

        requested_at = parser.parse(snapshot["snapshotJob"]["requestedAt"])

        started_at = parser.parse(snapshot["snapshotResult"]["startedAt"])
        finished_at = parser.parse(snapshot["snapshotResult"]["finishedAt"])

        time_took = finished_at - started_at

        # In general, a snapshot should have the same number of works as the
        # catalogue API.  There might be some drift, if new works appear in the
        # pipeline between the snapshot being taken and the reporter running,
        # but not much.  The threshold 25 is chosen somewhat arbitrarily.
        if api_document_count == snapshot_document_count:
            api_comparison = "same as the catalogue API"
        elif abs(api_document_count - snapshot_document_count) < 25:
            api_comparison = "almost the same as the catalogue API"
        else:
            api_comparison = f"*different from the catalogue API, which has {humanize.intcomma(api_document_count)}*"

        return "\n".join(
            [
                f"The latest snapshot is of index *{index_name}*, taken *{format_date(requested_at)}*.",
                f"• It is {humanize.naturalsize(s3_size)}",
                f"• It took {humanize.naturaldelta(time_took)} to create",
                f"• It contains {humanize.intcomma(snapshot_document_count)} documents ({api_comparison})",
            ]
        )

    if snapshots:
        latest_snapshot = snapshots[0]

        heading = ":white_check_mark: Catalogue Snapshot"
        message = _snapshot_message(latest_snapshot)
    else:
        kibana_logs_link = "https://logging.wellcomecollection.org/goto/ddc4dfc7308261cf17f956515ca1ce35"
        heading = ":interrobang: Catalogue Snapshot not found"
        message = f"No snapshot found within the last day. See logs: {kibana_logs_link}"

    return {
        "blocks": [
            {"type": "header", "text": {"type": "plain_text", "text": heading}},
            {"type": "section", "text": {"type": "mrkdwn", "text": message}},
        ]
    }


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
    elastic_index = os.environ["ELASTIC_INDEX"]

    elastic_client = get_elastic_client(elastic_secret_id)

    snapshots = get_snapshots(elastic_client, elastic_index)
    api_document_count = get_catalogue_api_document_count(endpoint="works")

    slack_payload = prepare_slack_payload(
        snapshots=snapshots, api_document_count=api_document_count
    )

    post_to_slack(slack_secret_id, slack_payload)


if __name__ == "__main__":
    main()
