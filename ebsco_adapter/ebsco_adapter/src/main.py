#!/usr/bin/env python3
import argparse
from datetime import datetime
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
from metrics import ProcessMetrics

ftp_server = os.environ.get("FTP_SERVER")
ftp_username = os.environ.get("FTP_USERNAME")
ftp_password = os.environ.get("FTP_PASSWORD")
ftp_remote_dir = os.environ.get("FTP_REMOTE_DIR")
sns_topic_arn = os.environ.get("OUTPUT_TOPIC_ARN")
reindex_topic_arn = os.environ.get("REINDEX_TOPIC_ARN")

s3_bucket = os.environ.get("S3_BUCKET", "wellcomecollection-platform-ebsco-adapter")
s3_prefix = os.environ.get("S3_PREFIX", "dev")

ftp_s3_prefix = os.path.join(s3_prefix, "ftp")
xml_s3_prefix = os.path.join(s3_prefix, "xml")


def run_process(temp_dir, ebsco_ftp, s3_store, sns_publisher, invoked_at):
    print("Running regular process ...")
    available_files = sync_and_list_files(temp_dir, ftp_s3_prefix, ebsco_ftp, s3_store)

    # Holding the connection open for the next step
    # is unnecessary, if we close here we avoid any
    # potential timeout issues with the connection.
    ebsco_ftp.quit()

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
            invoked_at,
        )

    return {}


def run_reindex(s3_store, sns_publisher, invoked_at, reindex_type, ids=None):
    assert reindex_type in ["full", "partial"], "Invalid reindex type"
    assert (
        ids is not None or reindex_type == "full"
    ), "You must provide IDs for partial reindexing"

    print(f"Running reindex with type {reindex_type} and ids {ids} ...")
    files = list_files(ftp_s3_prefix, s3_store)
    batches = find_notified_and_completed_flag(files, xml_s3_prefix, s3_store)
    notified_and_completed_batches = [
        batch["date"]
        for batch in batches
        if batch["notified_completed"] and batch["unpacking_completed"]
    ]

    print(f"Found {notified_and_completed_batches} notified and completed batches.")

    if not notified_and_completed_batches:
        print("No valid batch to reindex, stopping!")
        return {}

    most_recent_batch = max(notified_and_completed_batches)
    print(f"Attempting reindex for most recent batch: {most_recent_batch}")

    completed_flag_path = os.path.join(
        xml_s3_prefix, most_recent_batch, "completed.flag"
    )
    print(f"Loading completed flag file ({completed_flag_path}) ...")
    completed_flag = s3_store.load_file(completed_flag_path)
    print(f"Completed flag loaded, found {len(completed_flag)} records.")

    if ids is not None:
        # Include only the IDs that are in the list provided if any
        completed_flag = {
            id: record for id, record in completed_flag.items() if id in ids
        }
        print(
            f"Finding matches for {len(ids)} IDs, found {len(completed_flag)} matches."
        )
        print(f"IDs not found: {set(ids) - set(completed_flag.keys())}")

    update_notifier(
        {"updated": completed_flag, "deleted": None},
        most_recent_batch,
        s3_store,
        s3_bucket,
        xml_s3_prefix,
        sns_publisher,
        invoked_at,
    )


# This is required to ensure that the datetime is in the correct format
# for the update_notifier function, Python's datetime.isoformat() does not
# include the 'Z' at the end of the string for older versions of Python.
def _get_iso8601_invoked_at():
    invoked_at = datetime.utcnow().isoformat()
    if invoked_at[-1] != "Z":
        invoked_at += "Z"
    return invoked_at


if __name__ == "__main__":
    event = None
    context = SimpleNamespace(invoked_function_arn=None)

    # Parse command line arguments for running locally
    parser = argparse.ArgumentParser(description="Perform reindexing operations")
    parser.add_argument(
        "--reindex-type",
        type=str,
        choices=["full", "partial"],
        help="Type of reindexing (full or partial)",
    )
    parser.add_argument(
        "--reindex-ids",
        type=str,
        help="Comma-separated list of IDs to reindex (for partial)",
    )
    parser.add_argument(
        "--scheduled-invoke",
        action="store_true",
        help="To run a regular process invocation, without reindexing.",
    )

    invoked_at = _get_iso8601_invoked_at()
    args = parser.parse_args()

    process_type = None
    reindex_ids = None
    sns_publisher = None

    if args.reindex_type:
        process_type = f"reindex-{args.reindex_type}"
        sns_publisher = SnsPublisher(reindex_topic_arn)
    if args.reindex_ids:
        reindex_ids = args.reindex_ids.split(",")
        reindex_ids = [rid.strip() for rid in reindex_ids]
    elif args.scheduled_invoke:
        process_type = "scheduled"
        sns_publisher = SnsPublisher(sns_topic_arn)

    with ProcessMetrics(
        process_type
    ) as metrics, tempfile.TemporaryDirectory() as temp_dir, EbscoFtp(
        ftp_server, ftp_username, ftp_password, ftp_remote_dir
    ) as ebsco_ftp:
        s3_store = S3Store(s3_bucket)

        if args.reindex_type:
            run_reindex(
                s3_store,
                sns_publisher,
                invoked_at,
                args.reindex_type,
                reindex_ids,
            )
        elif args.scheduled_invoke:
            run_process(temp_dir, ebsco_ftp, s3_store, sns_publisher, invoked_at)
