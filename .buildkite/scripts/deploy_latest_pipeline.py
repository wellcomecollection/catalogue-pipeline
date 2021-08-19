#!/usr/bin/env python3

import json
import re
import urllib.request

import boto3


def get_internal_model_version():
    with open("project/Dependencies.scala") as infile:
        internal_model_line = next(
            line for line in infile if line.strip().startswith("val internalModel =")
        )

        return internal_model_line.split()[-1].strip('"')


def get_current_pipeline_date():
    """
    Uses the /_elasticConfig endpoint on the prod API to find out what
    pipeline is currently feeding the API.

    Returns the pipeline date, e.g. 2021-08-19

    """
    resp = json.load(
        urllib.request.urlopen(
            "https://api.wellcomecollection.org/catalogue/v2/_elasticConfig"
        )
    )

    # The works index name is a string that looks something like
    #
    #     works-indexed-2021-08-19
    #
    index_regex = re.compile(r"^works-indexed-(?P<date>\d{4}-\d{2}-\d{2})$")
    return index_regex.match(resp["worksIndex"]).group("date")


def get_secret_value(sess, *, secret_id):
    """
    Retrieve a secret from Secrets Manager.
    """
    client = sess.client("secretsmanager")
    return client.get_secret_value(SecretId=secret_id)["SecretString"]


if __name__ == "__main__":
    pipeline_date = get_current_pipeline_date()
    print(f"The current prod pipeline is {pipeline_date}")

    internal_model_version = get_internal_model_version()
    print(f"The current version of internal model is {internal_model_version}")

    sess = boto3.Session()
    print(get_secret_value(sess, secret_id=f"elasticsearch/pipeline_storage_{pipeline_date}/read_only/es_username"))
