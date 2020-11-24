#!/bin/env python3

from sys import maxsize
import click
import boto3
from wellcome_aws_utils.sns_utils import publish_sns_message


STORAGE_ROLE = "arn:aws:iam::975596993436:role/storage-read_only"
STORAGE_VHS = "vhs-storage-manifests-2020-07-24"

CATALOGUE_ROLE = "arn:aws:iam::760097843905:role/platform-developer"
CATALOGUE_TOPIC = "arn:aws:sns:eu-west-1:760097843905:mets_adapter_repopulate_script_output"


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

    def __init__(self):
        self.dynamodb = aws_resource("dynamodb", role_arn=STORAGE_ROLE)

    @property
    def paginator(self):
        return self.dynamodb.get_paginator("scan")

    def scan(self):
        for page in self.paginator.paginate(TableName=STORAGE_VHS):
            for item in page["Items"]:
                space, externalIdentifier = item["id"].split("/", 1)
                yield (space, externalIdentifier)


class MessagePublisher:

    def __init__(self):
        self.sns = aws_resource("sns", role_arn=CATALOGUE_ROLE)

    def publish(self, space, id):
        msg = {"context": {"storageSpace": space, "externalIdentifier": id}}
        publish_sns_message(self.sns, CATALOGUE_TOPIC, msg, "populate_mets.py")


@click.command()
@click.option(
    "--mode",
    type=click.Choice(["complete", "partial"]),
    required=True,
    default="partial",
    prompt="Every record from the storage-service VHS (complete) or just a few (partial)?",
    help="Should this populate from every record in the storage-service VHS?",
)
def main(mode):
    if mode == "partial":
        num_records = click.prompt("How many records do you want to send?", default=10)
    else:
        num_records = maxsize
    scanner = StorageManifestScanner()
    publisher = MessagePublisher()
    for i, (space, id) in zip(range(1, num_records + 1), scanner.scan()):
        click.echo(f"{click.style(str(i), fg='blue')}: {space}/{id}")
        publisher.publish(space, id)


if __name__ == "__main__":
    main()
