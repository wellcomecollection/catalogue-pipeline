#!/usr/bin/env python3

import boto3
import click
import elasticsearch
import json
from datetime import datetime
from pathlib import Path
from elasticsearch import Elasticsearch

test_docs_dir = Path("./test_documents")
image_document_prefixes = ["images.examples.color-filter-tests", "images.similar-features"]


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


def get_secret_string(session, *, secret_id):
    """
    Look up the value of a SecretString in Secrets Manager.
    """
    secrets = session.client("secretsmanager")
    return secrets.get_secret_value(SecretId=secret_id)["SecretString"]


def get_pipeline_storage_es_client(reindex_date):
    """
    Returns an Elasticsearch client for the pipeline-storage cluster.
    """
    session = get_session_with_role("arn:aws:iam::760097843905:role/platform-developer")

    secret_prefix = f"elasticsearch/pipeline_storage_{reindex_date}"

    host = get_secret_string(session, secret_id=f"{secret_prefix}/public_host")
    port = get_secret_string(session, secret_id=f"{secret_prefix}/port")
    protocol = get_secret_string(session, secret_id=f"{secret_prefix}/protocol")
    username = get_secret_string(
        session, secret_id=f"{secret_prefix}/read_only/es_username"
    )
    password = get_secret_string(
        session, secret_id=f"{secret_prefix}/read_only/es_password"
    )
    return Elasticsearch(f"{protocol}://{host}:{port}", basic_auth=(username, password))


@click.command()
@click.argument("pipeline_date")
def main(pipeline_date):
    pipeline_client = get_pipeline_storage_es_client(pipeline_date)
    for child in test_docs_dir.iterdir():
        if not child.is_file():
            continue
        if not any(child.name.startswith(prefix) for prefix in image_document_prefixes):
            continue

        test_document = json.load(child.open())

        try:
            pipeline_doc = pipeline_client.get(
                index=f"images-indexed-{pipeline_date}",
                id=test_document["id"]
            )["_source"]
        except elasticsearch.NotFoundError:
            print(f"⚠️  {test_document['id']} ({child.name}) does not exist in the {pipeline_date} index")
            continue

        test_document["document"] = pipeline_doc
        test_document["createdAt"] = datetime.now().strftime('%Y-%m-%dT%H:%M:%SZ')

        with child.open("w+") as out_file:
            json.dump(test_document, out_file, indent=2, ensure_ascii=False)
            out_file.write("\n")

        print(f"✅ Updated {child.name}")


if __name__ == "__main__":
    main()
