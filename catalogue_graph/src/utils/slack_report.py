import boto3
import typing
import requests

def publish_report(report: list[typing.Any], slack_secret: str) -> None:
    secretsmanager = boto3.Session(profile_name="platform-developer").client("secretsmanager")
    slack_endpoint = secretsmanager.get_secret_value(SecretId=slack_secret)[
        "SecretString"
    ]

    requests.post(slack_endpoint, json={"blocks": report})