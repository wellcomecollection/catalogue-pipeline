#!/bin/env python3
"""
Tell the METS adapter to fetch manifests from the Wellcome storage service.
"""

import itertools
import json

import boto3
import click
import tqdm


STORAGE_ROLE = "arn:aws:iam::975596993436:role/storage-read_only"
STORAGE_VHS = "vhs-storage-manifests-2020-07-24"

CATALOGUE_ROLE = "arn:aws:iam::760097843905:role/platform-developer"
CATALOGUE_TOPIC = (
    "arn:aws:sns:eu-west-1:760097843905:mets_adapter_repopulate_script_output"
)


def aws_resource(name, *, role_arn):
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


class StorageManifestReader:
    """
    Reads manifests from the Storage Service VHS.
    """

    def _dynamodb(self):
        return aws_resource("dynamodb", role_arn=STORAGE_ROLE)

    def total_manifests(self):
        table_resp = self._dynamodb().describe_table(TableName=STORAGE_VHS)
        return table_resp["Table"]["ItemCount"]

    def all_manifests(self):
        paginator = self._dynamodb().get_paginator("scan")

        for page in paginator.paginate(TableName=STORAGE_VHS):
            for item in page["Items"]:
                space, externalIdentifier = item["id"].split("/", 1)
                yield (space, externalIdentifier)


def publish_messages(manifests):
    """
    Publish a series of messages to SNS.
    """
    sns = aws_resource("sns", role_arn=CATALOGUE_ROLE)

    for space, externalIdentifier in manifests:
        # This should mimic the format of the BagRegistrationNotification in
        # the storage service.
        message = {"space": space, "externalIdentifier": externalIdentifier}

        resp = sns.publish(
            TopicArn=CATALOGUE_TOPIC,
            MessageStructure="json",
            Message=json.dumps({"default": json.dumps(message)}),
        )

        if resp["ResponseMetadata"]["HTTPStatusCode"] != 200:
            raise RuntimeError(f"Error response from SNS: {resp!r}")


@click.group()
def cli():
    pass


@cli.command()
@click.option("--count", help="How many manifests should we fetch?", default=10)
def partial(count):
    """
    Fetch a few records from the storage service.  Useful for testing the METS adapter.
    """
    reader = StorageManifestReader()

    manifests = tqdm.tqdm(itertools.islice(reader.all_manifests(), count), total=count)

    publish_messages(manifests)


@cli.command()
def complete():
    """
    Fetch every record from the storage service.  Useful for repopulating the catalogue.
    """
    reader = StorageManifestReader()

    manifests = tqdm.tqdm(reader.all_manifests(), total=reader.total_manifests())

    publish_messages(manifests)


@cli.command()
@click.argument("bag_ids", nargs=-1)
def specific(bag_ids):
    """
    Fetch specific records from the storage service.
    """
    manifests = tqdm.tqdm([id.split("/", 1) for id in bag_ids])

    publish_messages(manifests)


if __name__ == "__main__":
    cli()
