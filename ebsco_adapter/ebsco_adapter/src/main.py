#!/usr/bin/env python3
import tempfile
import os

from types import SimpleNamespace

import pymarc

from ebsco_ftp import EbscoFtp
from s3_store import S3Store
from sync_files import sync_files

ftp_server = os.environ.get("FTP_SERVER")
ftp_username = os.environ.get("FTP_USERNAME")
ftp_password = os.environ.get("FTP_PASSWORD")
ftp_remote_dir = os.environ.get("FTP_REMOTE_DIR")

s3_bucket = os.environ.get("S3_BUCKET")
s3_prefix = os.environ.get("S3_PREFIX")

ftp_s3_prefix = os.path.join(s3_prefix, 'ftp')
xml_s3_prefix = os.path.join(s3_prefix, 'xml')


def extract_marc_records(file_path):
    with open(file_path, 'rb') as marc_file:
        reader = pymarc.marcxml.parse_xml_to_array(marc_file)
        for record in reader:
            control_field = record['001']

            if not control_field:
                raise ValueError("Control field not found in record")

            yield {
                "control_number": control_field.data,
                "xml_record": pymarc.marcxml.record_to_xml(record)
            }


def lambda_handler(event, context):
    with tempfile.TemporaryDirectory() as temp_dir:
        with EbscoFtp(ftp_server, ftp_username, ftp_password, ftp_remote_dir) as ebsco_ftp:
            s3_store = S3Store(s3_bucket)

            uploaded_files = sync_files(temp_dir, ftp_s3_prefix, ebsco_ftp, s3_store)
            for uploaded_file in uploaded_files:
                # get the date and convert it to yyyy-mm-dd iso date string
                date_batch_name = uploaded_file["date"].strftime('%Y-%m-%d')
                print(date_batch_name)
                for record in extract_marc_records(uploaded_file["download_location"]):
                    print(record)
                    break
                break

    return {}


if __name__ == "__main__":
    event = []
    context = SimpleNamespace(invoked_function_arn=None)
    lambda_handler(event, context)
