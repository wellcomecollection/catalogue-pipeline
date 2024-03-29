#!/usr/bin/env python
"""
This script is run by a Terraform local-exec provisioner to create roles/users in
an Elastic Cloud cluster immediately after it's been created.
"""

import secrets
import sys

import click
from elasticsearch import Elasticsearch

from aws import get_session, read_secret, write_secret


DESCRIPTION = "Credentials for the pipeline-storage-{date} Elasticsearch cluster"

WORK_INDICES = ["source", "merged", "denormalised", "identified", "indexed"]

IMAGE_INDICES = ["initial", "augmented", "indexed"]

WORK_INDEX_PATTERN = "works-{index}*"

IMAGE_INDEX_PATTERN = "images-{index}*"


SERVICES = {
    # This role isn't used by applications, but instead provided to give developer scripts
    # read-only access to the pipeline_storage cluster.
    "read_only": [f"works-{index}_read" for index in WORK_INDICES] + [f"images-{index}_read" for index in IMAGE_INDICES] + ["viewer"],
}

CATALOGUE_SERVICES = {"catalogue_api", "concepts_api", "snapshot_generator"}


def store_secret(session, *, secret_id, secret_value, description):
    """
    Store a key/value pair in Secrets Manager.
    """
    secrets_client = session.client("secretsmanager")

    resp = secrets_client.put_secret_value(
        SecretId=secret_id, SecretString=secret_value
    )

    if resp["ResponseMetadata"]["HTTPStatusCode"] != 200:
        raise RuntimeError(f"Unexpected error from PutSecretValue: {resp}")

    click.echo(f"Stored secret {click.style(secret_id, 'yellow')}")


def create_roles(es, index):
    """
    Create read and write roles for a given work type.
    """
    for role_suffix, privileges in [("read", ["read"]), ("write", ["all"])]:
        role_name = f"{index}_{role_suffix}"

        es.security.put_role(
            name=role_name,
            body={"indices": [{"names": [f"{index}*"], "privileges": privileges}]},
        )

        yield role_name


def create_user(es, username, roles):
    """
    Creates a user with the given roles.  Returns a (username, password) pair.
    """
    password = secrets.token_hex()
    es.security.put_user(username=username, body={"password": password, "roles": roles})

    return (username, password)


if __name__ == '__main__':
    try:
        pipeline_date = sys.argv[1]
    except IndexError:
        sys.exit(f"Usage: {__file__} <PIPELINE_DATE>")

    platform_session = get_session(
        role_arn="arn:aws:iam::760097843905:role/platform-developer"
    )
    catalogue_session = get_session(
        role_arn="arn:aws:iam::756629837203:role/catalogue-developer"
    )

    secret_prefix = f"elasticsearch/pipeline_storage_{pipeline_date}"

    es_host = read_secret(platform_session, secret_id=f"{secret_prefix}/public_host")
    es_protocol = read_secret(platform_session, secret_id=f"{secret_prefix}/protocol")
    es_port = read_secret(platform_session, secret_id=f"{secret_prefix}/port")

    username = read_secret(platform_session, secret_id=f"{secret_prefix}/es_username")
    password = read_secret(platform_session, secret_id=f"{secret_prefix}/es_password")

    endpoint = f"{es_protocol}://{es_host}:{es_port}"

    es = Elasticsearch(endpoint, http_auth=(username, password))

    newly_created_roles = set()

    for index in WORK_INDICES:
        for r in create_roles(es, index=f"works-{index}"):
            newly_created_roles.add(r)
            click.echo(f"Created role {click.style(r, 'green')}")

    for index in IMAGE_INDICES:
        for r in create_roles(es, index=f"images-{index}"):
            newly_created_roles.add(r)
            click.echo(f"Created role {click.style(r, 'green')}")

    print("")

    newly_created_usernames = []

    for username, roles in SERVICES.items():
        newly_created_usernames.append(create_user(es, username=username, roles=roles))
        click.echo(f"Created user {click.style(username, 'green')}")

    print("")

    for username, password in newly_created_usernames:
        if username in CATALOGUE_SERVICES:
            session = catalogue_session
        else:
            session = platform_session

        write_secret(
            session,
            secret_id=f"{secret_prefix}/{username}/es_username",
            secret_value=username,
            description=DESCRIPTION.format(date=pipeline_date)
        )

        write_secret(
            session,
            secret_id=f"{secret_prefix}/{username}/es_password",
            secret_value=password,
            description=DESCRIPTION.format(date=pipeline_date)
        )
