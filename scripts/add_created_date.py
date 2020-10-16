import json
import math

import boto3
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
    )


class StorageManifestScanner:
    storage_role = "arn:aws:iam::975596993436:role/storage-read_only"
    adapter_role = "arn:aws:iam::760097843905:role/platform-read_only"
    storage_table_name = "vhs-storage-manifests-2020-07-24"
    adapter_table_name = "mets-adapter-store"

    def __init__(self):
        self.s3 = aws_resource("s3", role_arn=self.storage_role)
        dynamodb_storage_role = aws_resource("dynamodb", self.storage_role)
        self.dynamodb_adapter_role = aws_resource("dynamodb", self.adapter_role).meta.client
        self.storage_table = dynamodb_storage_role.Table(self.storage_table_name)


    @property
    def paginator(self):
        return self.dynamodb_adapter_role.get_paginator("scan")


    def scan(self):
        for page in self.paginator.paginate(TableName=self.adapter_table_name):
            for item in page["Items"]:
                id = item["id"]
                if 'createdDate' not in item['payload'].keys():
                    stored = self.storage_table.query(
                                             KeyConditionExpression=Key('id').eq(f"digitised/{id}"),
                                             Limit = 1,
                                             ScanIndexForward = False)["Items"][0]
                    try:
                        bucket = stored["payload"]["bucket"]
                    except KeyError:
                        bucket = stored["payload"]["namespace"]
                    try:
                        key = stored["payload"]["key"]
                    except KeyError:
                        key = stored["payload"]["path"]

                    bag_manifest = json.loads(self.s3.Object(bucket, key).get()["Body"].read().decode("utf-8"))
                    yield id, bag_manifest["createdDate"]


def update(table, batch_writer, id, created_date):
    item = table.get_item(
        Key={"id": id}
    )["Item"]
    created_date_milliseconds = math.floor(maya.parse(created_date).datetime().timestamp() * 1000)
    item['payload']['createdDate'] = created_date_milliseconds
    batch_writer.put_item(Item=item)


def main():
    scanner = StorageManifestScanner()
    dynamodb = aws_resource("dynamodb", platform_dev_role)
    table = dynamodb.Table(adapter_table_name)
    with table.batch_writer() as batch_writer:
        with yaspin():
            for id, created_date in scanner.scan():
                update(table, batch_writer, id, created_date)



if __name__ == "__main__":
    main()
