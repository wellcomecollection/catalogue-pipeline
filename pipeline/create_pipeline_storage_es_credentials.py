#!/usr/bin/env python
"""
This script creates roles and users that allow services in the pipeline
to talk to indices in the catalogue-pipeline-storage cluster.

You can use your own credentials, or generate a new password for
the 'elastic' user by logging in to the Elastic Cloud console.
"""

import functools
import secrets

import boto3
from botocore.exceptions import ClientError
import click
from elasticsearch import Elasticsearch
import hyperlink


WORK_INDICES = ["source", "merged", "denormalised", "identified"]

IMAGE_INDICES = ["initial", "augmented"]

WORK_INDEX_PATTERN = "works-{index}*"

IMAGE_INDEX_PATTERN = "images-{index}*"


SERVICES = {
    "transformer": ["source_write"],
    "id_minter": ["source_read", "identified_write"],
    "matcher": ["identified_read"],
    "merger": ["identified_read", "merged_write"],
    "router": ["merged_read", "denormalised_write"],
    "relation_embedder": ["merged_read", "denormalised_write"],
    "work_ingestor": ["denormalised_read"],
    "inferrer": ["initial_read", "augmented_write"],
    "image_ingestor": ["augmented_read"],
    # This role isn't used by applications, but instead provided to give developer scripts
    # read-only access to the pipeline_storage cluster.
    "dev": [f"{index}_read" for index in IMAGE_INDICES + WORK_INDICES],
}

DEFAULT_DESCRIPTION = "Credentials for the pipeline-storage Elasticsearch cluster"


@functools.lru_cache()
def get_aws_client(resource, *, role_arn):
    """
    Get a boto3 client authenticated against the given role.
    """
    sts_client = boto3.client("sts")
    assumed_role_object = sts_client.assume_role(
        RoleArn=role_arn, RoleSessionName="AssumeRoleSession1"
    )
    credentials = assumed_role_object["Credentials"]
    return boto3.client(
        resource,
        aws_access_key_id=credentials["AccessKeyId"],
        aws_secret_access_key=credentials["SecretAccessKey"],
        aws_session_token=credentials["SessionToken"],
    )


def store_secret(secret_id, secret_value, description=DEFAULT_DESCRIPTION):
    """
    Store a key/value pair in Secrets Manager.
    """
    # We store a secret in both the platform and catalogue accounts.
    # This is a stopgap until all the pipeline services are running in
    # the catalogue account; eventually we should remove them from
    # the platform account.
    #
    # See https://github.com/wellcomecollection/platform/issues/4823
    for role_arn in [
        "arn:aws:iam::760097843905:role/platform-developer",
        "arn:aws:iam::756629837203:role/catalogue-developer",
    ]:
        secrets_client = get_aws_client("secretsmanager", role_arn=role_arn)

        try:
            resp = secrets_client.create_secret(
                Name=secret_id, Description=description, SecretString=secret_value
            )
        except ClientError as err:
            if err.response["Error"]["Code"] == "ResourceExistsException":
                resp = secrets_client.put_secret_value(
                    SecretId=secret_id, SecretString=secret_value
                )

                if resp["ResponseMetadata"]["HTTPStatusCode"] != 200:
                    raise RuntimeError(f"Unexpected error from PutSecretValue: {resp}")
            else:
                raise
        else:
            if resp["ResponseMetadata"]["HTTPStatusCode"] != 200:
                raise RuntimeError(f"Unexpected error from CreateSecret: {resp}")

    click.echo(f"Stored secret {click.style(secret_id, 'yellow')}")


def create_roles(es, index):
    """
    Create read and write roles for a given work type.
    """
    for role_suffix, privileges in [("read", ["read"]), ("write", ["all"])]:
        role_name = f"{index}_{role_suffix}"
        index_pattern = (
            IMAGE_INDEX_PATTERN if index in IMAGE_INDICES else WORK_INDEX_PATTERN
        )
        index_name = index_pattern.format(index=index)

        es.security.put_role(
            role_name,
            body={"indices": [{"names": [index_name], "privileges": privileges}]},
        )

        yield role_name


def create_user(es, username, roles):
    """
    Creates a user with the given roles.  Returns a (username, password) pair.
    """
    password = secrets.token_hex()
    es.security.put_user(username=username, body={"password": password, "roles": roles})

    return (username, password)


@click.command()
@click.option(
    "--username", default="elastic", prompt="What is your Elasticsearch username?"
)
@click.option(
    "--password", hide_input=True, prompt="What is your Elasticsearch password?"
)
@click.option("--endpoint", prompt="What is your Elasticsearch endpoint?")
@click.option("--deployment-id", default="pipeline_storage", prompt="What is your Elasticsearch deployment ID?")
def main(username, password, endpoint, deployment_id):
    url = hyperlink.URL.from_text(endpoint)
    host = url.host
    protocol = url.scheme
    port = str(url.port)

    click.echo(
        f"Detected the host as {click.style(url.host, 'blue')}, the port as "
        f"{click.style(port, 'blue')} and the protocol as {click.style(protocol, 'blue')}."
    )
    click.confirm("Are these correct?", abort=True)

    print("")

    store_secret(secret_id=f"catalogue/{deployment_id}/es_host", secret_value=host)

    store_secret(secret_id=f"catalogue/{deployment_id}/es_port", secret_value=port)

    store_secret(
        secret_id=f"catalogue/{deployment_id}/es_protocol", secret_value=protocol
    )

    print("")

    es = Elasticsearch(endpoint, http_auth=(username, password))

    newly_created_roles = set()
    for index in WORK_INDICES + IMAGE_INDICES:
        for r in create_roles(es, index=index):
            newly_created_roles.add(r)
            click.echo(f"Created role {click.style(r, 'green')}")

    print("")

    newly_created_usernames = []

    for username, roles in SERVICES.items():
        if not set(roles).issubset(newly_created_roles):
            raise RuntimeError(
                f"Unrecognised roles: {set(roles) - newly_created_roles}"
            )

        newly_created_usernames.append(create_user(es, username=username, roles=roles))
        click.echo(f"Created user {click.style(username, 'green')}")

    print("")

    for username, password in newly_created_usernames:
        store_secret(
            secret_id=f"catalogue/{deployment_id}/{username}/es_username",
            secret_value=username,
        )

        store_secret(
            secret_id=f"catalogue/{deployment_id}/{username}/es_password",
            secret_value=password,
        )


if __name__ == "__main__":
    main()
