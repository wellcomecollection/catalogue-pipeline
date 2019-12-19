#!/bin/env python3

from sys import maxsize
import click
import boto3
from botocore.exceptions import ClientError
from wellcome_aws_utils.sns_utils import publish_sns_message
from decimal import Decimal


def aws_resource(name, role_arn):
    role = boto3.client("sts").assume_role(
        RoleArn=role_arn, RoleSessionName="AssumeRoleSession1"
    )
    credentials = role["Credentials"]
    return boto3.resource(
        name,
        aws_access_key_id=credentials["AccessKeyId"],
        aws_secret_access_key=credentials["SecretAccessKey"],
        aws_session_token=credentials["SessionToken"],
    ).meta.client


class StorageManifestScanner:

    role = "arn:aws:iam::975596993436:role/storage-read_only"
    vhs = "vhs-storage-manifests"

    def __init__(self, start_record, num_records):
        self.dynamodb = aws_resource("dynamodb", self.role)
        self.start_record = start_record
        self.num_records = num_records

    def _paginate(self):
        paginator = self.dynamodb.get_paginator("scan")
        if self.start_record:
            exclusive_start_key = {
                'id': self.start_record[0],
                'version': Decimal(str(self.start_record[1]))
            }
            return paginator.paginate(TableName=self.vhs, Limit=self.num_records,ExclusiveStartKey=exclusive_start_key)
        else:
            return paginator.paginate(TableName=self.vhs, Limit=self.num_records)

    def scan(self):
        for page in self._paginate():
            with click.progressbar(page["Items"]) as items:
                for item in items:
                    space, id = item["id"].split("/")
                    yield (space, id)
            self.start_record=page["LastEvaluatedKey"]

class MessagePublisher:

    role = "arn:aws:iam::760097843905:role/platform-developer"
    topic_arn = "arn:aws:sns:eu-west-1:760097843905:mets_temp_test_topic"

    def __init__(self):
        self.sns = aws_resource("sns", self.role)

    def publish(self, space, id):
        msg = {"context": {"storageSpace": space, "externalIdentifier": id}}
        publish_sns_message(self.sns, self.topic_arn, msg, "populate_mets.py")


@click.command()
@click.option(
    "--mode",
    type=click.Choice(["complete", "partial"]),
    required=True,
    default="partial",
    prompt="Every record from the storage-service VHS (complete) or just a few (partial)?",
    help="Should this populate from every record in the storage-service VHS?",
)
@click.option(
    "--start-record",
    required=True,
    type=(str, int),
    default=(),
    prompt="Which record do you want to start from (supply id and version)?",
)
def main(mode, start_record):
    if mode == "partial":
        num_records = click.prompt("How many records do you want to send?", default=10)
    else:
        num_records = maxsize

    scanner = StorageManifestScanner(start_record, num_records)
    publisher = MessagePublisher()
    try:
        for i, (space, id) in zip(range(1, num_records + 1), scanner.scan()):
            publisher.publish(space, id)
    except ClientError:
        click.echo(f"Refresh your credentials then press enter to continue")
        click.pause()
        main(mode, scanner.start_record)
    except:
        click.echo(f"Failed! You may restart from token {scanner.start_record}")
        raise


if __name__ == "__main__":
    main()
