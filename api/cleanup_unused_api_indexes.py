#!/usr/bin/env python

import json

import boto3


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

    return credentials


if __name__ == "__main__":
    session = get_session_with_role(
        role_arn="arn:aws:iam::760097843905:role/platform-developer"
    )

    print(get_api_es_client(session))
