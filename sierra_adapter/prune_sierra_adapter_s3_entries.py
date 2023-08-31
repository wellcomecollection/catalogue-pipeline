#!/usr/bin/env python
"""
This script walks the S3 buckets for the Sierra adapter, and deletes unused objects.

Here, "unused" means an object which is a lower version than the object referred to
in the DynamoDB table.
"""

import boto3
import humanize
from more_itertools import chunked
import tqdm

dynamo = boto3.resource("dynamodb").meta.client
s3 = boto3.client("s3")


def get_s3_objects(bucket):
    """
    Generates all the S3 objects in a bucket.
    """
    for page in s3.get_paginator("list_objects_v2").paginate(Bucket=bucket):
        yield from page["Contents"]


def scan_table(table):
    """
    Generates all the items in a DynamoDB table.
    """
    for page in dynamo.get_paginator("scan").paginate(TableName=table):
        yield from page["Items"]


def get_keys_to_delete(bucket, dynamo_lookup):
    """
    Generates S3 objects that can be deleted.
    """
    for s3_obj in tqdm.tqdm(get_s3_objects(bucket)):
        bib_number, version, _ = s3_obj["Key"].split("/")

        try:
            dynamo_entry = dynamo_lookup[bib_number]
        except KeyError:
            continue

        # If the Dynamo entry refers to something in a completely different bucket,
        # something weird is going on.  Skip this object.
        if bucket != dynamo_entry["bucket"]:
            continue

        # If the object is the same or greater than the version of the existing
        # object, skip it -- we could be looking at an object written to S3 but not
        # yet indexed in DynamoDB.
        if int(version) < dynamo_entry["version"]:
            yield s3_obj


if __name__ == "__main__":
    sierra_vhs = [
        {
            "bucket": "wellcomecollection-vhs-sierra-sierra-adapter-20200604",
            "table": "vhs-sierra-sierra-adapter-20200604",
        },
        {
            "bucket": "wellcomecollection-vhs-sierra-items-sierra-adapter-20200604",
            "table": "vhs-sierra-items-sierra-adapter-20200604",
        },
    ]

    total_bytes_deleted = 0
    total_objects_deleted = 0

    try:
        for vhs in sierra_vhs:
            print(f"Fetching entries from DynamoDB for {vhs['table']}...")
            dynamo_lookup = {}

            for row in tqdm.tqdm(scan_table(table=vhs["table"])):
                try:
                    dynamo_lookup[row["id"]] = {
                        "bucket": row["payload"]["namespace"],
                        "key": row["payload"]["path"],
                        "version": int(row["version"]),
                    }
                except KeyError:
                    dynamo_lookup[row["id"]] = {
                        "bucket": row["payload"]["bucket"],
                        "key": row["payload"]["key"],
                        "version": int(row["version"]),
                    }

            print(f"Deleting objects from s3://{vhs['bucket']}...")
            for objset in chunked(
                get_keys_to_delete(bucket=vhs["bucket"], dynamo_lookup=dynamo_lookup),
                n=1000,
            ):
                s3.delete_objects(
                    Bucket=vhs["bucket"],
                    Delete={"Objects": [{"Key": s3_obj["Key"]} for s3_obj in objset]},
                )

                total_bytes_deleted += sum(s3_obj["Size"] for s3_obj in objset)
                total_objects_deleted += len(objset)
    finally:
        print(
            f"Deleted {humanize.intcomma(total_objects_deleted)} object{'s' if total_objects_deleted > 1 else ''}, "
            f"saving {humanize.naturalsize(total_bytes_deleted)}"
        )
