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
    index_regex = re.compile(r"^works-indexed-(?P<date>\d{4}-\d{2}-\d{2})$")
    pipeline_date = index_regex.match(index_name).group("date")
    print(f"The current prod pipeline is {pipeline_date}")

    print()

    sess = boto3.Session()
    index_versions = get_index_internal_model_versions(
        sess, pipeline_date=pipeline_date, index_name=index_name
    )
    print(f"The current index supports the following internal models:")
    for name, git_hash in sorted(index_versions.items()):
        print(f" - {name.ljust(20)}: {git_hash}")

    print()

    latest_version, latest_commit = max(index_versions.items())
    print(f"The following files have changed since {latest_version}/{latest_commit}:")
    command = ["git", "diff", "--name-only", latest_commit, "HEAD"]
    changed_paths = [
        line.strip().decode("utf8")
        for line in subprocess.check_output(command).splitlines()
    ]
    for p in changed_paths:
        print(f" - {p}")

    print()

    internal_model_folder = json.load(open(".sbt_metadata/internal_model.json"))[
        "folder"
    ]
    internal_model_paths = [
        p
        for p in changed_paths
        if p.startswith(os.path.join(internal_model_folder, "src", "main"))
    ]

    if not internal_model_paths:
        print("Nothing in internal_model has changed.  It is SAFE to deploy.")

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
    else:
        print("The following files in internal_model have changed:")
        for p in internal_model_paths:
            print(f" - {p}")
        print("It MAY NOT BE SAFE to deploy.")
        print()
        print("BuildKite cannot determine if these changes are compatible with the")
        print("existing pipeline.")
        print()
        print(
            "Please inspect the changes and do a manual deploy if you know these changes"
        )
        print("are safe to deploy.")
        sys.exit(1)
