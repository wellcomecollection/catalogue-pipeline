#!/usr/bin/env python3

import json
import re
import urllib.request

import boto3
import httpx


def get_internal_model_version():
    with open("project/Dependencies.scala") as infile:
        internal_model_line = next(
            line for line in infile if line.strip().startswith("val internalModel =")
        )

        return internal_model_line.split()[-1].strip('"')


def get_current_index_name():
    """
    Returns index which is currently being served by the API.
    """
    resp = httpx.get("https://api.wellcomecollection.org/catalogue/v2/_elasticConfig")
    resp.raise_for_status()
    return resp.json()["worksIndex"]


def get_secret_value(sess, *, secret_id):
    """
    Retrieve a secret from Secrets Manager.
    """
    client = sess.client("secretsmanager")
    return client.get_secret_value(SecretId=secret_id)["SecretString"]


def get_index_internal_model_versions(sess, *, pipeline_date, index_name):
    """
    Returns a list of internal_model versions supported by this index.
    """
    secret_prefix = f"elasticsearch/pipeline_storage_{pipeline_date}"

    username = get_secret_value(sess, secret_id=f"{secret_prefix}/read_only/es_username")
    password = get_secret_value(sess, secret_id=f"{secret_prefix}/read_only/es_password")
    host = get_secret_value(sess, secret_id=f"{secret_prefix}/public_host")

    resp = httpx.get(
        f"https://{host}:9243/{index_name}",
        auth=(username, password)
    )
    resp.raise_for_status()

    return resp.json()[index_name]["mappings"]["_meta"]


if __name__ == "__main__":
    index_name = get_current_index_name()
    print(f"The current index name is {index_name}")

    # The works index name is a string that looks something like
    #
    #     works-indexed-2021-08-19
    #
    index_regex = re.compile(r"^works-indexed-(?P<date>\d{4}-\d{2}-\d{2})$")
    pipeline_date = index_regex.match(index_name).group("date")
    print(f"The current prod pipeline is {pipeline_date}")

    internal_model_version = get_internal_model_version()
    print(f"The current version of internal model is {internal_model_version}")

    sess = boto3.Session()
    index_versions = get_index_internal_model_versions(
        sess, pipeline_date=pipeline_date, index_name=index_name
    )
    print(f"The current index supports the following internal models:")
    for name, git_hash in sorted(index_versions.items()):
        print(f" - {name.ljust(20)}: {git_hash}")
