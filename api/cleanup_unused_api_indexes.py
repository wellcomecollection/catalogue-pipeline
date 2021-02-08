#!/usr/bin/env python

import click
import datetime
import json

import boto3
import httpx


def get_session_with_role(role_arn):
    """
    Returns a boto3.Session that uses the given role ARN.
    """
    sts_client = boto3.client("sts")

    assumed_role_object = sts_client.assume_role(
        RoleArn=role_arn, RoleSessionName="AssumeRoleSession1"
    )
    credentials = assumed_role_object["Credentials"]
    return boto3.Session(
        aws_access_key_id=credentials["AccessKeyId"],
        aws_secret_access_key=credentials["SecretAccessKey"],
        aws_session_token=credentials["SessionToken"],
    )


def get_api_es_client(session):
    """
    Returns an Elasticsearch client for the catalogue cluster.
    """
    secrets = session.client("secretsmanager")

    credentials = json.loads(
        secrets.get_secret_value(SecretId="elasticsearch/api_cleanup/credentials")[
            "SecretString"
        ]
    )

    return httpx.Client(
        base_url=credentials["endpoint"],
        auth=(credentials["username"], credentials["password"]),
    )


def list_indexes(es_client):
    """
    Returns a list of indexes in the Elasticsearch cluster, sorted by index name.
    """
    resp = es_client.get("/_cat/indices", params={"format": "json"})
    resp.raise_for_status()

    return sorted(
        [
            {
                "name": r["index"],
                "size": r["store.size"],
                "doc_count": int(r['docs.count']),
            }
            for r in resp.json()
        ],
        key=lambda r: r["name"],
    )


def get_index_date(api_url):
    """
    Return the date of the index that this
    """
    search_templates_resp = httpx.get(f"https://{api_url}/catalogue/v2/search-templates.json")
    index_name = search_templates_resp.json()["templates"][0]["index"]

    # Check it looks like works-YYYY-MM-DD
    prefix, date = index_name.split("-", 1)
    assert prefix == "works"
    datetime.datetime.strptime(date, "%Y-%M-%d")

    return date


if __name__ == "__main__":
    session = get_session_with_role(
        role_arn="arn:aws:iam::760097843905:role/platform-developer"
    )

    es_client = get_api_es_client(session)

    prod_index_date = get_index_date("api.wellcomecollection.org")
    stage_index_date = get_index_date("api-stage.wellcomecollection.org")

    click.echo(
        "The prod API is reading from %s; the staging API is reading from %s" %
        (click.style(f"works-{prod_index_date}"), click.style(f"works-{stage_index_date}"))
    )

    from pprint import pprint

    pprint(list_indexes(es_client))
