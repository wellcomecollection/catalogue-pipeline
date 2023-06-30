#!/usr/bin/env python
"""
In our Sierra holdings, we have electronic access to journals which are
under a fixed-time embargo -- e.g. you can access anything published up
to four years before the current date (or 1460 days).

Sierra is somehow tracking this internally, but it's not exposed directly
in the holdings record.  Sierra fills in the 863 field based on when you
request the record (e.g. "you can see issues up to 28 May 2017"), then
adds a note in subfield $x:

    863    …|xChronology adjusted by 1460 day embargo period

If you fetch the same record the following day, it will change the date
in the 863 (e.g. "you can see issues up to 29 May 2017"), but it doesn't
update the modified date in the holdings record -- so the adapter won't
fetch the update.

This Lambda looks in the reporting cluster to find holdings records that
have this note, then asks the Sierra adapter to re-fetch those records.

"""

import datetime
import json
import os

import boto3
import httpx


def get_secret_string(session, *, secret_id):
    secretsmanager = session.client("secretsmanager")
    return secretsmanager.get_secret_value(SecretId=secret_id)["SecretString"]


def get_holdings_under_embargo(*, es_host, es_username, es_password):
    resp = httpx.request(
        "GET",
        f"https://{es_host}:9243/sierra_varfields/_search",
        auth=(es_username, es_password),
        json={
            "query": {
                "bool": {
                    "must": [
                        {"match": {"varField.subfields.content": "Chronology adjusted"}}
                    ],
                    "filter": {"term": {"parent.recordType": "holdings"}},
                }
            },
            "size": 10000,
            "_source": ["parent.id", "varField.subfields.content"],
        },
    )
    resp.raise_for_status()

    hits = resp.json()["hits"]

    # This would represent a massive increase in the number of holdings
    # we have; currently we have just 167.
    #
    # If we hit this assertion, it means we need to start paginating the
    # Elasticsearch response, but I don't want to do that right now.
    assert hits["total"]["value"] <= 10000, "Too many results!"

    holdings_ids = set(hit["_source"]["parent"]["id"] for hit in hits["hits"])

    mget_resp = httpx.request(
        "GET",
        f"https://{es_host}:9243/sierra_holdings/_mget",
        auth=(es_username, es_password),
        json={"docs": [{"_id": id, "_source": "updatedDate"} for id in holdings_ids]},
    )
    mget_resp.raise_for_status()

    for doc in mget_resp.json()['docs']:
        yield datetime.datetime.strptime(
            doc["_source"]["updatedDate"], "%Y-%m-%dT%H:%M:%SZ"
        )

    # We additionally fetch any holdings that have been modified in the
    # last 90 days.  The Sierra updatedDate field for holdings records is
    # quite flaky and not always accurate; this is a stopgap to catch
    # cases where the record changes (e.g. in the monthly coverage load)
    # that Sierra doesn't notice.
    #
    # See https://wellcome.slack.com/archives/C8X9YKM5X/p1688135850998959
    today = datetime.datetime.now()

    for days in range(90):
        yield today - datetime.timedelta(days=days)


def main(_event, _context):
    update_embargoed_holdings()


def update_embargoed_holdings():
    topic_arn = os.environ["HOLDINGS_READER_TOPIC_ARN"]

    session = boto3.Session()

    es_host = get_secret_string(session, secret_id="reporting/es_host")
    es_username = get_secret_string(
        session, secret_id="reporting/read_only/es_username"
    )
    es_password = get_secret_string(
        session, secret_id="reporting/read_only/es_password"
    )

    affected_holdings = get_holdings_under_embargo(
        es_host=es_host, es_username=es_username, es_password=es_password
    )

    # A bunch of these were modified on similar times on a small number
    # of dates (e.g. 2002-11-28 00:42:42, 00:46:38, 00:52:38), so find
    # a unique set of dates.
    affected_dates = {d.date() for d in affected_holdings}

    sns_client = session.client("sns")
    for d in affected_dates:
        sns_client.publish(
            TopicArn=topic_arn,
            Message=json.dumps(
                {
                    "start": d.strftime("%Y-%m-%dT00:00:01+00:00"),
                    "end": d.strftime("%Y-%m-%dT23:59:59+00:00"),
                }
            ),
        )


if __name__ == "__main__":
    update_embargoed_holdings()
