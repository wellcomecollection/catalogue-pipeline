#!/usr/bin/env python3
import tempfile
import os

from types import SimpleNamespace

from ebsco_ftp import EbscoFtp
from s3_store import S3Store
from sns_publisher import SnsPublisher
from sync_files import sync_and_list_files
from extract_marc import extract_marc_records
from compare_uploads import compare_uploads
from update_notifier import update_notifier

ftp_server = os.environ.get("FTP_SERVER")
ftp_username = os.environ.get("FTP_USERNAME")
ftp_password = os.environ.get("FTP_PASSWORD")
ftp_remote_dir = os.environ.get("FTP_REMOTE_DIR")
sns_topic_arn = os.environ.get("OUTPUT_TOPIC_ARN")

s3_bucket = os.environ.get("S3_BUCKET")
s3_prefix = os.environ.get("S3_PREFIX")

ftp_s3_prefix = os.path.join(s3_prefix, 'ftp')
xml_s3_prefix = os.path.join(s3_prefix, 'xml')


def lambda_handler(event, context):
    with tempfile.TemporaryDirectory() as temp_dir:
        with EbscoFtp(ftp_server, ftp_username, ftp_password, ftp_remote_dir) as ebsco_ftp:
            s3_store = S3Store(s3_bucket)
            sns_publisher = SnsPublisher(sns_topic_arn)

            available_files = sync_and_list_files(temp_dir, ftp_s3_prefix, ebsco_ftp, s3_store)
            updates = compare_uploads(available_files, extract_marc_records, xml_s3_prefix, temp_dir, s3_store)
            if updates is not None:
                update_notifier(updates, updates['notify_for_batch'], s3_store, s3_bucket, xml_s3_prefix, sns_publisher)

    return {}


if __name__ == "__main__":
    event = []
    context = SimpleNamespace(invoked_function_arn=None)
    lambda_handler(event, context)
