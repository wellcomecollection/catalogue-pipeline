#!/usr/bin/env python
"""
This script deletes unused indexes in the API cluster.

This reduces disk usage, and in our experience reduces CPU and memory pressure in the cluster.

This script will go through all the indexes in the cluster.  If it thinks an index is safe
to delete (i.e. it's not being used in the API and is older than the API indexes), it will
ask you to confirm you want to delete it.
"""

import click
import json

import boto3
import httpx
import humanize


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
                "doc_count": int(r["docs.count"]),
            }
            for r in resp.json()
        ],
        key=lambda r: r["name"],
    )


def get_index_name(api_url):
    """
    Return the name of the index that this instance of the API is reading from.
    """
    search_templates_resp = httpx.get(
        f"https://{api_url}/catalogue/v2/search-templates.json"
    )
    return search_templates_resp.json()["templates"][0]["index"]


def maybe_cleanup_index(es_client, *, idx, prod_index_name, stage_index_name):
    """
    Decide if we're going to clean up this index, and if so, double-check with the user.
    """
    if not idx["name"].startswith(("works-", "images-")):
        return

    prod_works_index_name = prod_index_name
    prod_images_index_name = prod_index_name.replace("works-", "images-")

    stage_works_index_name = stage_index_name
    stage_images_index_name = stage_index_name.replace("works-", "images-")

    click.echo(
        f"\nConsidering %s (%s docs, %s)"
        % (
            click.style(idx["name"], "blue"),
            humanize.intcomma(idx["doc_count"]),
            idx["size"],
        )
    )

    # We should never delete the prod or staging indexes -- this would cause an instant
    # outage in the API, which is very bad.
    if idx["name"] == prod_works_index_name or idx["name"] == prod_images_index_name:
        click.echo(
            f"This index will {click.style('not be deleted', 'green')} -- it is the prod API"
        )
        return

    if idx["name"] == stage_works_index_name or idx["name"] == stage_images_index_name:
        click.echo(
            f"This index will {click.style('not be deleted', 'green')} -- it is the stage API"
        )
        return

    # We consider deleting an index if it sorts lexicographically lower than both the
    # prod and staging APIs.  e.g. works-2001-01-01 < works-2002-02-02
    #
    # We skip indexes that sort higher, because this might be an index that we're currently
    # reindexing into, but we haven't pointed an API at yet.  In general, indexes go forward,
    # not backward.
    #
    # We still ask the user to confirm they really want to delete this index, just in case.
    # We should never offer to delete an index that would cause an outage, but they might
    # skip an index if, say, they've only just promoted a new index to prod and they
    # want to be able to roll back to this index.
    if (
        idx["name"].startswith("works-")
        and idx["name"] < prod_works_index_name
        and idx["name"] < stage_works_index_name
    ):
        result = click.confirm(
            f"This index is {click.style('older', 'red')} than the current APIs.  Delete it?"
        )
        if result:
            es_client.delete(f"/{idx['name']}")
        return

    if (
        idx["name"].startswith("images-")
        and idx["name"] < prod_images_index_name
        and idx["name"] < stage_images_index_name
    ):
        result = click.confirm(
            f"This index is {click.style('older', 'red')} than the current APIs.  Delete it?"
        )
        if result:
            es_client.delete(f"/{idx['name']}")
        return

    # If we get this far, we're not going to delete this index. Log and return.
    click.echo(f"This index will {click.style('not be deleted', 'green')}")
    return


if __name__ == "__main__":
    session = get_session_with_role(
        role_arn="arn:aws:iam::760097843905:role/platform-developer"
    )

    es_client = get_api_es_client(session)

    prod_index_name = get_index_name("api.wellcomecollection.org")
    stage_index_name = get_index_name("api-stage.wellcomecollection.org")

    click.echo(
        "The prod API  is reading from %s" % click.style(prod_index_name, "blue")
    )
    click.echo(
        "The stage API is reading from %s" % click.style(stage_index_name, "blue")
    )

    for idx in list_indexes(es_client):
        maybe_cleanup_index(
            es_client,
            idx=idx,
            prod_index_name=prod_index_name,
            stage_index_name=stage_index_name,
        )

    #
    # from pprint import pprint
    #
    # pprint(list_indexes(es_client))
