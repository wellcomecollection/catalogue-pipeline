import datetime
import json
import os
import sys
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
    updated_date_format = '%Y-%m-%dT%H:%M:%SZ'

    updated_date = f'[{window["start"].strftime(updated_date_format)},{window["end"].strftime(updated_date_format)}]'

    print(f"Fetching updated records --> {updated_date}")
    for record in client.get_objects(
        f"/{resource_type}",
        params={
            "fields": sierra_fields,
            "updatedDate": updated_date,
        },
    ):
        yield record

    # Because filtering by updatedDate doesn't catch deleted records, we
    # need to make a separate query that filters on deletedDate.
    #
    # The deletedDate in Sierra is only a date supporting day-level
    # granularity.  We want a query like this:
    #
    #       deletedDate=2022-07-16
    #       deletedDate=[2022-07-16,2022-07-20]
    #
    # Note: this means we may process the same deletion multiple times
    # in a day.  We consider this to be acceptable -- deletions are
    # idempotent and have a minimal impact on the rest of the pipeline.
    deleted_start = window["start"].date()
    deleted_end = window["end"].date()

    if deleted_start == deleted_end:
        deleted_date = deleted_start.isoformat()
    else:
        deleted_date = f"[{deleted_start.isoformat()},{deleted_end.isoformat()}]"

    print(f"Fetching deleted records --> {deleted_date}")

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
        # The choice of "end of day" for deleted records is to ensure
        # that deletions take precedence over updates.
        #
        # In the rest of the Sierra reader, we use the modifiedDate field
        # to order updates.  Deletions always take precedence, because
        # deleting a record is a one-way operation -- once deleted, a record
        # can't be undeleted or modified again.
        #
        # Example:
        #
        #   1.  A librarian updates a record at 12:00.  When we get the
        #       record from the Sierra API, it has a timestamp:
        #
        #           updatedDate  => "2001-01-01T12:00:00Z"
        #
        #       We send this date directly to the downstream apps:
        #
        #           modifiedDate => "2001-01-01T12:00:00Z"
        #
        #   2.  Another librarian deletes the record at 13:00.  When we
        #       get the record from the Sierra API, it just has a date:
        #
        #           deletedDate  => "2001-01-01"
        #
        #       We send a 23:59:59 timestamp to the downstream apps:
        #
        #           modifiedDate => "2001-01-01T23:59:59Z"
        #
        #       Because this modifiedDate is newer, this ensures the deletion
        #       will replace the update we saw earlier in the day.
        #
        modified_date = record["deletedDate"] + "T23:59:59Z"
    except KeyError:
        modified_date = record["updatedDate"]

    return json.dumps(
        {
            "id": record["id"],
            "data": json.dumps(record),
            "bibIds": [str(bib_id) for bib_id in record.get("bibIds", [])],
            "modifiedDate": modified_date,
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


def run_with(json_window):
    window = {
        k: datetime.datetime.fromisoformat(v) for k, v in json_window.items()
    }

    client = catalogue_client()

    sns_client = boto3.client("sns")
    s3_client = boto3.client("s3")

    topic_arn = os.environ["TOPIC_ARN"]
    resource_type = os.environ["RESOURCE_TYPE"]
    bucket_name = os.environ["READER_BUCKET"]

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

    print(f"Window is complete --> {json.dumps(json_window)}")

    # Once the window is completed, we write an empty object to S3 to
    # mark it as such.  The progress reporter can read these files
    # and determine whether any windows have been missed.
    #
    # Note: the slightly odd date serialisation here is a legacy of
    # the old Scala reader; we might want to consider changing it later.
    start = window["start"].isoformat().replace(":", "-")
    end = window["end"].isoformat().replace(":", "-")

    s3_client.put_object(
        Bucket=bucket_name,
        Key=f"windows_{resource_type}_complete/{start}__{end}",
        Body=b"",
    )


def main(event, context):
    for window in get_windows(event):
        run_with(window)


if __name__ == '__main__':
    try:
        window = json.loads(sys.argv[1])
    except (IndexError, ValueError):
        sys.exit(f"Usage: {__file__} <WINDOW>")

    run_with(window)
