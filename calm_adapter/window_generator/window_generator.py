#!/bin/env python3

from datetime import date, timedelta
import json

import click
import boto3
from tqdm import tqdm


class WindowGenerator:

    topic_arn = "arn:aws:sns:eu-west-1:760097843905:calm-windows"
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"

    def __init__(self, start, end=None):
        self.start = start
        self.end = end or start
        assert self.start <= self.end, "Start window is after end window"
        self.client = self.get_sns_client()

    def run(self):
        for window in tqdm(list(self.windows)):
            self.send_window_to_sns(window)

    @property
    def windows(self):
        while self.start <= self.end:
            yield self.start
            self.start += timedelta(days=1)

    def send_window_to_sns(self, window):
        msg = json.dumps({"date": window.isoformat()})
        self.client.publish(
            TopicArn=self.topic_arn, Message=msg, Subject=f"Window sent by {__file__}"
        )

    def get_sns_client(self):
        sts = boto3.client("sts")
        role = sts.assume_role(
            RoleArn=self.role_arn, RoleSessionName="AssumeRoleSession1"
        )
        credentials = role["Credentials"]
        return boto3.client(
            "sns",
            aws_access_key_id=credentials["AccessKeyId"],
            aws_secret_access_key=credentials["SecretAccessKey"],
            aws_session_token=credentials["SessionToken"],
        )


@click.group()
def cli():
    pass


@cli.command()
def full_ingest():
    start = date(2015, 9, 30)  # First date returning any records
    end = date.today()
    WindowGenerator(start, end).run()


@cli.command()
def today():
    start = date.today()
    WindowGenerator(start).run()


@cli.command()
@click.argument("start", type=click.DateTime(["%Y-%m-%d"]))
@click.argument("end", type=click.DateTime(["%Y-%m-%d"]))
def date_range(start, end):
    WindowGenerator(start.date(), end.date()).run()


if __name__ == "__main__":
    cli()
