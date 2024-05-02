#!/usr/bin/env python3
import argparse
import tempfile
import os

from types import SimpleNamespace

from ebsco_ftp import EbscoFtp
from s3_store import S3Store
from sns_publisher import SnsPublisher
from sync_files import sync_and_list_files, list_files
from extract_marc import extract_marc_records
from compare_uploads import compare_uploads, find_notified_and_completed_flag
from update_notifier import update_notifier

ftp_server = os.environ.get("FTP_SERVER")
ftp_username = os.environ.get("FTP_USERNAME")
ftp_password = os.environ.get("FTP_PASSWORD")
ftp_remote_dir = os.environ.get("FTP_REMOTE_DIR")
sns_topic_arn = os.environ.get("OUTPUT_TOPIC_ARN")

s3_bucket = os.environ.get("S3_BUCKET", "wellcomecollection-platform-ebsco-adapter")
s3_prefix = os.environ.get("S3_PREFIX", "dev")

ftp_s3_prefix = os.path.join(s3_prefix, "ftp")
xml_s3_prefix = os.path.join(s3_prefix, "xml")


def run_process(temp_dir, ebsco_ftp, s3_store, sns_publisher):
    print("Running regular process ...")
    available_files = sync_and_list_files(
        temp_dir, ftp_s3_prefix, ebsco_ftp, s3_store
    )
    updates = compare_uploads(
        available_files, extract_marc_records, xml_s3_prefix, temp_dir, s3_store
    )
    if updates is not None:
        update_notifier(
            updates,
            updates["notify_for_batch"],
            s3_store,
            s3_bucket,
            xml_s3_prefix,
            sns_publisher,
        )

    return {}


def run_reindex(s3_store, sns_publisher, reindex_type, ids=None):
    assert reindex_type in ["full", "partial"], "Invalid reindex type"
    assert ids is not None or reindex_type == "full", "You must provide IDs for partial reindexing"

    print(f"Running reindex with type {reindex_type} and ids {ids} ...")
    files = list_files(ftp_s3_prefix, s3_store)
    batches = find_notified_and_completed_flag(files, xml_s3_prefix, s3_store)
    notified_and_completed_batches = [
        batch["date"] for batch in batches if batch["notified_completed"] and batch["unpacking_completed"]
    ]

    print(f"Found {notified_and_completed_batches} notified and completed batches.")

    if not notified_and_completed_batches:
        print("No valid batch to reindex, stopping!")
        return {}

    most_recent_batch = max(notified_and_completed_batches)
    print(f"Attempting reindex for most recent batch: {most_recent_batch}")

    print("Loading completed flag file ...")
    completed_flag_path = os.path.join(xml_s3_prefix, most_recent_batch, "completed.flag")
    print(completed_flag_path)
    completed_flag = s3_store.load_file(completed_flag_path)
    print(f"Completed flag loaded, found {len(completed_flag)} records.")

    if ids is not None:
        # Include only the IDs that are in the list provided if any
        completed_flag = {id: record for id, record in completed_flag.items() if id in ids}
        print(f"Finding matches for {len(ids)} IDs, found {len(completed_flag)} matches.")
        print(f"IDs not found: {set(ids) - set(completed_flag.keys())}")

    update_notifier(
        {"updated": completed_flag, "deleted": None},
        most_recent_batch,
        s3_store,
        s3_bucket,
        xml_s3_prefix,
        sns_publisher,
    )


def lambda_handler(event, context):
    print("Starting lambda_handler, got event: ", event)
    with tempfile.TemporaryDirectory() as temp_dir:
        with EbscoFtp(
                ftp_server, ftp_username, ftp_password, ftp_remote_dir
        ) as ebsco_ftp:
            s3_store = S3Store(s3_bucket)
            sns_publisher = SnsPublisher(sns_topic_arn)

            if event is not None and "reindex_type" in event:
                return run_reindex(s3_store, sns_publisher, event["reindex_type"], event.get("reindex_ids"))
            else:
                return run_process(temp_dir, ebsco_ftp, s3_store, sns_publisher)


if __name__ == "__main__":
    event = None
    context = SimpleNamespace(invoked_function_arn=None)

    # Parse command line arguments for running locally
    parser = argparse.ArgumentParser(description="Perform reindexing operations")
    parser.add_argument(
        "--reindex-type",
        type=str,
        choices=["full", "partial"],
        help="Type of reindexing (full or partial)"
    )
    parser.add_argument(
        "--reindex-ids",
        type=str,
        help="Comma-separated list of IDs to reindex (for partial)"
    )

    args = parser.parse_args()
    if args.reindex_type:
        reindex_ids = None
        if args.reindex_ids:
            reindex_ids = args.reindex_ids.split(",")
            reindex_ids = [rid.strip() for rid in reindex_ids]

        event = {
            "reindex_type": args.reindex_type,
            "reindex_ids": reindex_ids
        }

    lambda_handler(event, None)
