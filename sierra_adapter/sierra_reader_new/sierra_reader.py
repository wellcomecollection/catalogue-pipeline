import itertools
import json
import os
import uuid

import boto3

from client import catalogue_client


def get_windows(event):
    for record in event["Records"]:
        yield json.loads(record["Sns"]["Message"])


def get_sierra_records(client, window):
    resource_type = os.environ["RESOURCE_TYPE"]
    sierra_fields = os.environ["SIERRA_FIELDS"]

    print(f"Fetching updated records for {window}")
    for record in client.get_objects(
        f"/{resource_type}",
        params={
            "fields": sierra_fields,
            "updatedDate": f'[{window["start"]},{window["end"]}]',
        },
    ):
        yield record

    dates = {window["start"].split("T")[0], window["end"].split("T")[0]}

    print(f"Fetching deleted records for {dates}")
    for deleted_date in dates:
        for record in client.get_objects(
            f"/{resource_type}",
            params={"fields": sierra_fields, "deletedDate": deleted_date},
        ):
            yield record


def chunked_iterable(iterable, size):
    """
    Break an iterable into pieces of the given size.

    See https://alexwlchan.net/2018/iterating-in-fixed-size-chunks/
    """
    it = iter(iterable)
    while True:
        chunk = tuple(itertools.islice(it, size))
        if not chunk:
            break
        yield chunk


def main(event, context):
    client = catalogue_client()

    sns_client = boto3.client("sns")
    s3_client = boto3.client("s3")

    topic_arn = os.environ["TOPIC_ARN"]
    resource_type = os.environ["RESOURCE_TYPE"]
    sierra_fields = os.environ["SIERRA_FIELDS"]
    bucket_name = os.environ["READER_BUCKET"]

    for window in get_windows(event):
        affected_records = get_sierra_records(client, window)

        for batch in chunked_iterable(affected_records, size=10):
            batch_entries = [
                {"Id": str(uuid.uuid4()), "Message": json.dumps(record)}
                for record in batch
            ]

            resp = sns_client.publish_batch(
                TopicArn=topic_arn, PublishBatchRequestEntries=batch_entries
            )

            # This is to account for any failures in sending messages to SNS.
            # I've never actually seen this in practice so I've not written
            # any code to handle it but I include it just in case.
            assert len(resp["Failed"]) == 0, resp

        print(f"Window {window} is complete!")
        start = f"{window['start'][:19]}Z"
        end = f"{window['end'][:19]}Z"

        s3_client.put_object(
            Bucket=bucket_name,
            Key=f"windows_{resource_type}_complete/{start}__{end}",
            Body=b""
        )
