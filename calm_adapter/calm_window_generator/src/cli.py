#!/bin/env python3

from datetime import date

import boto3
import click
from tqdm import tqdm

from window_generator import (
    WindowGenerator as _WindowGenerator,
    CalmQuery,
    created_or_modified_date_range,
)


class WindowGenerator(_WindowGenerator):
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
    topic_arn = "arn:aws:sns:eu-west-1:760097843905:calm-windows"

    def __init__(self, queries):
        super().__init__(self.get_sns_client(), self.topic_arn, tqdm(list(queries)))

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
    queries = [
        CalmQuery.empty_created_and_modified_date(),
        *created_or_modified_date_range(start, end),
    ]
    WindowGenerator(queries).run()


@cli.command()
def today():
    start = date.today()
    queries = created_or_modified_date_range(start)
    WindowGenerator(queries).run()


@cli.command()
@click.argument("start", type=click.DateTime(["%Y-%m-%d"]))
@click.argument("end", type=click.DateTime(["%Y-%m-%d"]))
def date_range(start, end):
    queries = created_or_modified_date_range(start.date(), end.date())
    WindowGenerator(queries).run()


@cli.command()
def empty_created_and_modified_date():
    queries = [CalmQuery.empty_created_and_modified_date()]
    WindowGenerator(queries).run()


@cli.command()
@click.argument("ref_no")
def ref_no(ref_no):
    queries = [CalmQuery.ref_no(ref_no)]
    WindowGenerator(queries).run()


if __name__ == "__main__":
    cli()
