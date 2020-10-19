#!/bin/env python3
"""
This script has been used to update the METS adapter store to store createdDate in the payload.
It scans the METSadapter table and for each entry with no createdDate:
    * finds the latest version in the storage service VHS manifest table
    * uses the bucket and key in the payload to fetch the s3 content
    * extracts the createdDate from the s3 content
    * Stores the createdDate in the METS adapter table
This scripts assumes to be running on an insatnce wth permissions to modify the adapter
table and to assume the storage_read_only role
"""
import json
import math

import boto3
from botocore.exceptions import ClientError
from boto3.dynamodb.conditions import Key
import maya
from yaspin import yaspin

platform_dev_role = "arn:aws:iam::760097843905:role/platform-developer"
adapter_table_name = "mets-adapter-store"


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
        region_name="eu-west-1",
    )


class StorageManifestScanner:
    storage_role = "arn:aws:iam::975596993436:role/storage-read_only"
    storage_table_name = "vhs-storage-manifests-2020-07-24"
    adapter_table_name = "mets-adapter-store"

    def __init__(self):
        self.__get_storage_creds()
        self.dynamodb_adapter_client = boto3.client("dynamodb", region_name="eu-west-1")

    def __get_storage_creds(self):
        dynamodb_storage_role = aws_resource("dynamodb", self.storage_role)
        self.s3 = aws_resource("s3", role_arn=self.storage_role)
        self.storage_table = dynamodb_storage_role.Table(self.storage_table_name)

    @property
    def paginator(self):
        return self.dynamodb_adapter_client.get_paginator("scan")

    def __get_table_entry(self, id):
        try:
            stored = self.storage_table.query(
                KeyConditionExpression=Key("id").eq(f"digitised/{id}"),
                Limit=1,
                ScanIndexForward=False,
            )
            return stored["Items"][0]
        except ClientError:
            self.__get_storage_creds()
            return self.storage_table.query(
                KeyConditionExpression=Key("id").eq(f"digitised/{id}"),
                Limit=1,
                ScanIndexForward=False,
            )["Items"][0]

    def __get_s3_entry(self, bucket, key):
        try:
            return json.loads(
                self.s3.Object(bucket, key).get()["Body"].read().decode("utf-8")
            )
        except ClientError:
            self.__get_storage_creds()
            return json.loads(
                self.s3.Object(bucket, key).get()["Body"].read().decode("utf-8")
            )

    def scan(self):
        for page in self.paginator.paginate(TableName=self.adapter_table_name):
            for item in page["Items"]:
                id = item["id"]["S"]
                if "createdDate" not in item["payload"]["M"].keys():
                    stored = self.__get_table_entry(id)
                    try:
                        bucket = stored["payload"]["bucket"]
                    except KeyError:
                        bucket = stored["payload"]["namespace"]
                    try:
                        key = stored["payload"]["key"]
                    except KeyError:
                        key = stored["payload"]["path"]
                    bag_manifest = self.__get_s3_entry(bucket, key)
                    yield id, bag_manifest["createdDate"]


def update(table, batch_writer, id, created_date):
    item = table.get_item(Key={"id": id})["Item"]
    created_date_milliseconds = math.floor(
        maya.parse(created_date).datetime().timestamp() * 1000
    )
    item["payload"]["createdDate"] = created_date_milliseconds
    batch_writer.put_item(Item=item)


def main():
    scanner = StorageManifestScanner()
    dynamodb = boto3.resource("dynamodb", region_name="eu-west-1")
    table = dynamodb.Table(adapter_table_name)
    with table.batch_writer() as batch_writer:
        with yaspin():
            for id, created_date in scanner.scan():
                update(table, batch_writer, id, created_date)
        print("Done!")


if __name__ == "__main__":
    main()
