import json
import os
import uuid

import boto3

from client import catalogue_client


def get_windows(event):
    for record in event["Records"]:
        yield json.loads(record["Sns"]["Message"])


def get_sierra_records(client, window):
    """
    Fetch the records from Sierra for the given window.
    """
    resource_type = os.environ["RESOURCE_TYPE"]
    sierra_fields = os.environ["SIERRA_FIELDS"]

    # The updatedDate in Sierra is a timestamp supporting second-level
    # granularity, so we want a query like this:
    #
    #       updatedDate=[2022-07-16T23:59:00+00:00,2022-07-20T00:01:01+00:00]
    #
    # This only fetches Sierra records which have been updated but not
    # deleted.
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

    # Because filtering by updatedDate doesn't catch deleted records, we
    # need to make a separate query that filters on deletedDate.
    #
    # The deletedDate in Sierra is only a date supporting day-level
    # granularity.  We want a query like this:
    #
    #       deletedDate=2022-07-16
    #       deletedDate=[2022-07-16,2022-07-20]
    #
    print(f"Fetching deleted records for {dates}")
    deleted_start, _ = window["start"].split("T")
    deleted_end, _ = window["end"].split("T")

    if deleted_start == deleted_end:
        deleted_date = deleted_start
    else:
        deleted_date = f"[{deleted_start},{deleted_end}]"

    for record in client.get_objects(
        f"/{resource_type}",
        params={"fields": sierra_fields, "deletedDate": deleted_date},
    ):
        yield record


def to_scala_record(record):
    """
    Convert a record into the SierraRecord type expected by the Scala apps.

    Note: we may consider changing this at some point and simplifying
    what the Scala app expects, but for now this matches the messages sent
    by the existing Sierra reader.
    """
    try:
        return json.dumps(
            {
                "id": record["id"],
                "data": json.dumps(record),
                "bibIds": [],
                "modifiedDate": record["deletedDate"] + "T23:59:59Z",
            }
        )
    except KeyError:
        return json.dumps(
            {
                "id": record["id"],
                "data": json.dumps(record),
                "bibIds": [str(bib_id) for bib_id in record.get("bibIds", [])],
                "modifiedDate": record["updatedDate"],
            }
        )


def get_sns_batches(messages):
    """
    Given an iterable of SNS messages, group them into batches which
    are suitable for sending to the SNS PublishBatch API.

    There are two rules for batches:

    *   You can send up to 10 messages in a single batch
    *   A single batch can't exceed 256KB

    """
    this_batch = []

    for m in messages:
        this_message = [{"Id": str(uuid.uuid4()), "Message": m}]

        old_batch = this_batch
        new_batch = this_batch + this_message

        # If adding this message to the batch will push us over the 256KB
        # threshold, send all the other messages then start a new batch
        # with the latest message.
        if len(json.dumps(new_batch)) > 250 * 1024:
            yield old_batch
            this_batch = [this_message]
            continue

        # If there are 10 messages in this batch, send the entire batch
        # and then start a new batch.
        if len(new_batch) == 10:
            yield new_batch
            this_batch = []
            continue

    # If there are any leftover records, send them when we're done.
    if this_batch:
        yield this_batch


def main(event, context):
    client = catalogue_client()

    sns_client = boto3.client("sns")
    s3_client = boto3.client("s3")

    topic_arn = os.environ["TOPIC_ARN"]
    resource_type = os.environ["RESOURCE_TYPE"]
    bucket_name = os.environ["READER_BUCKET"]

    for window in get_windows(event):
        sierra_records = (
            to_scala_record(record) for record in get_sierra_records(client, window)
        )

        for batch_entries in get_sns_batches(sierra_records):
            resp = sns_client.publish_batch(
                TopicArn=topic_arn, PublishBatchRequestEntries=batch_entries
            )

            # This is to account for any failures in sending messages to SNS.
            # I've never actually seen this in practice so I've not written
            # any code to handle it but I include it just in case.
            assert len(resp["Failed"]) == 0, resp

        print(f"Window {window} is complete!")

        # Once the window is completed, we write an empty object to S3 to
        # mark it as such.  The progress reporter can read these files
        # and determine whether any windows have been missed.
        #
        # Note: the slightly odd date serialisation here is a legacy of
        # the old Scala reader; we might want to consider changing it later.
        start = window["start"].replace(":", "-")
        end = window["end"].replace(":", "-")

        s3_client.put_object(
            Bucket=bucket_name,
            Key=f"windows_{resource_type}_complete/{start}__{end}",
            Body=b"",
        )
