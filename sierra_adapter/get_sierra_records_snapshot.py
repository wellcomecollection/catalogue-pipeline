#!/usr/bin/env python
"""
This script creates a snapshot of Sierra records.  These snapshots are
useful if you want to analyse all the Sierra data in one go, e.g.

    "What are all the values in the 'location' field on an item record?"

You need AWS credentials that can use the platform-developer and
platform-read_only roles.

Creating the initial snapshot takes a *very* long time (think days), so we
keep a shared copy in S3.  This script will automatically download the
latest snapshot from S3, and upload an updated version.
"""

import datetime
import gzip
import json
import os
import uuid

import boto3
import click
import tqdm

from dynamo import DecimalEncoder, get_table_item_count, scan_table
from s3 import download_object_from_s3, upload_file_to_s3


READ_ONLY_ROLE_ARN = "arn:aws:iam::760097843905:role/platform-read_only"
DEVELOPER_ROLE_ARN = "arn:aws:iam::760097843905:role/platform-developer"

SNAPSHOT_BUCKET = "wellcomecollection-platform-infra"
SNAPSHOT_KEY = "sierra_records.json.gz"

LOCAL_SNAPSHOT_DIR = "_snapshots"
LOCAL_SNAPSHOT_PATH = os.path.join(LOCAL_SNAPSHOT_DIR, "sierra_records.json.gz")

TABLE_NAME = "vhs-sierra-sierra-adapter-20200604"

EXAMPLE_SCRIPT = f"""
import gzip
import json
import pprint


def get_sierra_records():
    for line in gzip.open({LOCAL_SNAPSHOT_PATH!r}):
        yield json.loads(line)


if __name__ == '__main__':
    for record in get_sierra_records():
        pprint.pprint(record)
        break
""".strip()


def get_aws_session(*, role_arn):
    sts_client = boto3.client("sts")
    assumed_role_object = sts_client.assume_role(
        RoleArn=role_arn, RoleSessionName="AssumeRoleSession1"
    )
    credentials = assumed_role_object["Credentials"]
    return boto3.Session(
        aws_access_key_id=credentials["AccessKeyId"],
        aws_secret_access_key=credentials["SecretAccessKey"],
        aws_session_token=credentials["SessionToken"],
    )


def download_existing_snapshot(session):
    click.echo(
        "*** Downloading existing snapshot from %s"
        % click.style(f"s3://{SNAPSHOT_BUCKET}/{SNAPSHOT_KEY}", "yellow")
    )
    os.makedirs(os.path.dirname(LOCAL_SNAPSHOT_PATH), exist_ok=True)

    download_object_from_s3(
        session, bucket=SNAPSHOT_BUCKET, key=SNAPSHOT_KEY, filename=LOCAL_SNAPSHOT_PATH
    )


def get_locations(session, *, table_name):
    """
    Gets the id -> location lookup from DynamoDB.

    The result is cached once a day, to avoid excessive lookups.
    """
    today = datetime.date.today().strftime("%Y-%m-%d")
    out_path = os.path.join(
        LOCAL_SNAPSHOT_DIR, f"dynamo__{table_name}__{today}.json.gz"
    )

    if not os.path.exists(out_path):
        print("***, Fetching items from DynamoDB...")
        item_count = get_table_item_count(session, table_name=TABLE_NAME)

        tmp_id = str(uuid.uuid4())
        tmp_path = f"{out_path}.{tmp_id}.tmp"

        os.makedirs(os.path.dirname(out_path), exist_ok=True)

        with gzip.open(tmp_path, "w") as outfile:
            for item in tqdm.tqdm(
                scan_table(session, table_name=table_name), total=item_count
            ):
                line = json.dumps(item, cls=DecimalEncoder) + "\n"
                outfile.write(line.encode("utf8"))

        os.rename(tmp_path, out_path)

    locations = {}

    for line in gzip.open(out_path, "rb"):
        row = json.loads(line)
        locations[row["id"]] = row["payload"]

    return locations


def fetch_transformable(s3_client, *, location):
    s3_obj = s3_client.get_object(Bucket=location["bucket"], Key=location["key"])

    transformable = json.load(s3_obj["Body"])

    if transformable.get("maybeBibRecord") is not None:
        transformable["maybeBibRecord"]["data"] = json.loads(
            transformable["maybeBibRecord"]["data"]
        )

    for item_record in transformable["itemRecords"].values():
        item_record["data"] = json.loads(item_record["data"])

    return transformable


def get_new_records(session, *, locations, snapshot_path):
    s3_client = session.client("s3")

    # First look at every record we've already saved, and compare it to
    # the current location.
    try:
        with gzip.open(snapshot_path) as infile:
            for line in infile:
                row = json.loads(line)

                latest_location = locations.pop(row['id'])

                if latest_location != row['location']:
                    row['location'] = latest_location
                    row['record'] = fetch_transformable(s3_client, location=latest_location)

                yield row
    except FileNotFoundError:
        pass

    # Now go through the list of remaining locations -- we've never
    # fetched these before, so we need to fetch them fresh.
    for id, location in locations.items():
        record = fetch_transformable(s3_client, location=location)

        record = {
            'bib': record.get('maybeBibRecord'),
            'items': {
                item_id: item_record['data']
                for item_id, item_record in record['itemRecords'].items()
            }
        }

        yield {
            'id': id,
            'location': location,
            'record': record,
        }


def freshen_sierra_records_snapshot(session, *, snapshot_path):
    locations = get_locations(session, table_name=TABLE_NAME)

    # Now go ahead and fetch all the Sierra records from S3.
    tmp_id = str(uuid.uuid4())
    tmp_path = f"{LOCAL_SNAPSHOT_PATH}.{tmp_id}.tmp"

    with gzip.open(tmp_path, "wb") as outfile:
        for record in tqdm.tqdm(
            get_new_records(session, locations=locations, snapshot_path=snapshot_path),
            total=len(locations)
        ):
            line = json.dumps(record) + "\n"
            outfile.write(line.encode("utf8"))

    os.rename(tmp_path, snapshot_path)


if __name__ == "__main__":
    read_only_session = get_aws_session(role_arn=READ_ONLY_ROLE_ARN)

    # download_existing_snapshot(read_only_session)

    if click.confirm(
        "Do you want to update the snapshot with the latest Sierra records? "
        "(Only do this if you're running on an EC2 instance)"
    ):
        freshen_sierra_records_snapshot(
            session=read_only_session, snapshot_path=LOCAL_SNAPSHOT_PATH
        )

        upload_file_to_s3(
            bucket=SNAPSHOT_BUCKET,
            key=SNAPSHOT_KEY,
            filename=LOCAL_SNAPSHOT_PATH
        )

    print("")
    print(f"Your snapshot has been saved to {LOCAL_SNAPSHOT_PATH}")
    print("")
    print("Here's some Python to get you started:")
    print("")
    print(click.style(EXAMPLE_SCRIPT, "blue"))
