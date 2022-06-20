#!/usr/bin/env python3

import json
import os
import re
import subprocess
import sys

import boto3
import httpx


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

    username = get_secret_value(
        sess, secret_id=f"{secret_prefix}/read_only/es_username"
    )
    password = get_secret_value(
        sess, secret_id=f"{secret_prefix}/read_only/es_password"
    )
    host = get_secret_value(sess, secret_id=f"{secret_prefix}/public_host")

    # If the index has a suffix letter like 'works-indexed-2021-08-16a', remove
    # that before querying the pipeline-storage cluster.
    #
    # Alternatively we could look at the API cluster, but that's more effort.
    index_name = index_name.rstrip("abcde")

    resp = httpx.get(f"https://{host}:9243/{index_name}", auth=(username, password))
    resp.raise_for_status()

    return resp.json()[index_name]["mappings"]["_meta"]


if __name__ == "__main__":
    index_name = get_current_index_name()
    print(f"The current index name is {index_name}")

    print()

    # The works index name is a string that looks something like
    #
    #     works-indexed-2021-08-19
    #
    index_regex = re.compile(r"^works-indexed-(?P<date>\d{4}-\d{2}-\d{2})[a-f]*$")
    pipeline_date = index_regex.match(index_name).group("date")
    print(f"The current prod pipeline is {pipeline_date}")

    print()

    root = (
        subprocess.check_output(["git", "rev-parse", "--show-toplevel"])
        .decode("utf8")
        .strip()
    )

    subprocess.check_call(
        [
            "docker",
            "run",
            "--rm",
            "--tty",
            "--volume",
            f"{root}:/repo",
            "--workdir",
            "/repo",
            "760097843905.dkr.ecr.eu-west-1.amazonaws.com/wellcome/weco-deploy:5.6",
            "--project-id",
            "catalogue_pipeline",
            "--confirm",
            "release-deploy",
            "--from-label",
            f"ref.{os.environ['BUILDKITE_COMMIT']}",
            "--environment-id",
            pipeline_date,
            "--description",
            os.environ["BUILDKITE_BUILD_URL"],
            "--confirmation-wait-for",
            "3600",
        ]
    )

