#!/usr/bin/env python3

from types import SimpleNamespace
import boto3
import os
import datetime
from ftplib import FTP
import tempfile

ftp_server = os.environ.get("FTP_SERVER")
ftp_username = os.environ.get("FTP_USERNAME")
ftp_password = os.environ.get("FTP_PASSWORD")
ftp_remote_dir = os.environ.get("FTP_REMOTE_DIR")

s3_bucket = os.environ.get("S3_BUCKET")
s3_prefix = os.environ.get("S3_PREFIX")

customer_id = os.environ.get("CUSTOMER_ID")


# files are in the format ebz-16539-20240117-Del.marc
# the second part is the customer id, the third part is an iso date, the fourth part is the change type
def get_marc_file_details(filename):
    try:
        filename_parts = filename.split("-")
        assert (
            len(filename_parts) == 4
        ), f"Unexpected name parts for file {filename}! Skipping..."

        assert filename.endswith(
            ".marc"
        ), f"Invalid file type for file {filename}! Skipping..."
        assert filename.startswith(
            f"ebz-{customer_id}"
        ), f"Unexpected file name for file {filename}! Skipping..."

        file_date = datetime.datetime.strptime(filename_parts[2], "%Y%m%d")
        return {
            "filename": filename,
            "date": file_date,
            "change": filename_parts[3].split(".")[0],
        }
    except AssertionError as e:
        print(e)
        return None
    except ValueError:
        print(f"Invalid date format for file {filename}! Skipping...")
        return None


def lambda_handler(event, context):
    valid_suffixes = [".csv", ".marc"]

    # get a list of files from the S3 bucket
    s3 = boto3.client("s3")
    s3_object_listing = s3.list_objects_v2(Bucket=s3_bucket, Prefix=s3_prefix)
    s3_files = [file["Key"].split("/")[-1] for file in s3_object_listing["Contents"]]
    s3_files = [file for file in s3_files if file.endswith(tuple(valid_suffixes))]

    print(f"Files in S3: {len(s3_files)}")

    # get a list of files from the FTP server with their file size
    ftp = FTP(ftp_server)
    ftp.login(ftp_username, ftp_password)
    ftp.cwd(ftp_remote_dir)
    ftp_files = []
    ftp.retrlines("LIST", ftp_files.append)
    ftp_files = [
        file.split()[-1] for file in ftp_files if file.endswith(tuple(valid_suffixes))
    ]

    print(f"Files in FTP: {len(ftp_files)}")

    # print the list of files that are in the FTP server but not in the S3 bucket
    files_to_download = list(set(ftp_files) - set(s3_files))
    print(f"Files to download: {len(files_to_download)}")

    files_by_date = {}
    for file in files_to_download:
        print(get_marc_file_details(file))

    # group the files by date
    for file in files_to_download:
        file_details = get_marc_file_details(file)
        if file_details:
            date = file_details["date"]
            if date in files_by_date:
                files_by_date[date].append(file_details)
            else:
                files_by_date[date] = [file_details]

    if len(files_to_download) > 0:
        with tempfile.TemporaryDirectory() as temp_dir:
            print(f"Downloading files to {temp_dir}")
            for file in files_to_download:
                with open(os.path.join(temp_dir, file), "wb") as f:
                    print(f"Downloading {file}...")
                    ftp.retrbinary(f"RETR {file}", f.write)
                    # Upload the file to S3
                    upload_location = f"{s3_prefix}/{file}"
                    print(f"Uploading {file} to S3...")
                    s3.upload_file(
                        os.path.join(temp_dir, file), s3_bucket, upload_location
                    )
    else:
        print("No files to download!")

    return {}


if __name__ == "__main__":
    event = []
    context = SimpleNamespace(invoked_function_arn=None)
    lambda_handler(event, context)
