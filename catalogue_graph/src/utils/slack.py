import typing
import requests

from utils.aws import get_secret

def publish_report(report: list[typing.Any], slack_secret: str) -> None:
    slack_endpoint = get_secret(slack_secret)

    requests.post(slack_endpoint, json={"blocks": report})
