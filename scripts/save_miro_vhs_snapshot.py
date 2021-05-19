#!/usr/bin/env python
"""
Save a complete copy of the Miro VHS table in S3.
"""

import datetime
import decimal
import gzip
import json

import tqdm

from _common import get_session


class DecimalEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, decimal.Decimal) and int(obj) == obj:
            return int(obj)


def scan_table(session, *, TableName, **kwargs):
    """
    Generates all the items in a DynamoDB table.

    :param dynamo_client: A boto3 client for DynamoDB.
    :param TableName: The name of the table to scan.

    Other keyword arguments will be passed directly to the Scan operation.
    See https://boto3.amazonaws.com/v1/documentation/api/latest/reference/services/dynamodb.html#DynamoDB.Client.scan

    """
    dynamodb_client = session.resource("dynamodb").meta.client
    paginator = dynamodb_client.get_paginator("scan")

    for page in paginator.paginate(TableName=TableName, **kwargs):
        yield from page["Items"]


if __name__ == "__main__":
    read_only_session = get_session(
        role_arn="arn:aws:iam::760097843905:role/platform-read_only"
    )
    dev_session = get_session(
        role_arn="arn:aws:iam::760097843905:role/platform-developer"
    )

    today = datetime.date.today()

    out_path = f"vhs-sourcedata-miro-{today}.json.gz"

    with gzip.open(out_path, "w") as outfile:
        for item in tqdm.tqdm(
            scan_table(read_only_session, TableName="vhs-sourcedata-miro")
        ):
            line = json.dumps(item, cls=DecimalEncoder) + "\n"
            outfile.write(line.encode("utf8"))

    s3_client = dev_session.client("s3")

    s3_client.upload_file(
        Filename=out_path,
        Bucket="wellcomecollection-platform-infra",
        Key=f"dynamodb_backups/{out_path}",
    )
