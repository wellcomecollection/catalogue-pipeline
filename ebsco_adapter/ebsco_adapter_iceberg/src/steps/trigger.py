import argparse
import os
import re
import tempfile
from datetime import datetime
from typing import Any

import boto3
from pydantic import BaseModel

from ebsco_ftp import EbscoFtp
from steps.loader import EbscoAdapterLoaderEvent
from utils.aws import get_ssm_parameter, list_s3_keys

ssm_param_prefix = "/catalogue_pipeline/ebsco_adapter"
s3_bucket = os.environ.get("S3_BUCKET", "wellcomecollection-platform-ebsco-adapter")
s3_prefix = os.environ.get("S3_PREFIX", "dev")
ftp_s3_prefix = os.path.join(s3_prefix, "ftp_v2")


class EbscoAdapterTriggerConfig(BaseModel):
    is_local: bool = False


class EbscoAdapterTriggerEvent(BaseModel):
    time: str


def get_most_recent_valid_file(filenames: list[str]) -> str | None:
    """Filter valid files, sort by date (newest first), and return the most recent one."""
    """Valid files are in the format ebz-s7451719-20240322-1.xml"""

    # Extract valid files with their parsed dates
    valid_files = [
        (filename, datetime.strptime(match.group(1), "%Y%m%d"))
        for filename in filenames
        if (match := re.match(r"^ebz-s7451719-(\d{8})-.*\.xml$", filename))
    ]

    # Return the filename with the most recent date, or None if no valid files
    return max(valid_files, key=lambda x: x[1])[0] if valid_files else None


def sync_files(
    ebsco_ftp: EbscoFtp, target_directory: str, s3_bucket: str, s3_prefix: str
) -> str:
    ftp_files = ebsco_ftp.list_files()

    most_recent_ftp_file = get_most_recent_valid_file(ftp_files)

    if most_recent_ftp_file is None:
        raise ValueError("No valid files found on FTP server")

    print(f"Most recent ftp file: {most_recent_ftp_file}")

    s3_store = boto3.client("s3")
    s3_key = f"{s3_prefix}/{most_recent_ftp_file}"

    # Check if the file already exists in S3
    try:
        s3_store.head_object(Bucket=s3_bucket, Key=s3_key)
        print(f"File {most_recent_ftp_file} already exists in S3. No need to download.")
        # we return the S3 location of the most recent file
        most_recent_s3_object = get_most_recent_valid_file(
            [key.split("/")[-1] for key in list_s3_keys(s3_bucket, s3_prefix)]
        )
        return f"s3://{s3_bucket}/{s3_prefix}/{most_recent_s3_object}"
    except Exception as e:
        if "NoSuchKey" in str(e):
            print(
                f"File {most_recent_ftp_file} not found in S3. Will download and upload."
            )
        else:
            print(f"Error checking S3: {e}. Will proceed with download and upload.")

    # Download the most recent file from FTP
    try:
        download_location = ebsco_ftp.download_file(
            most_recent_ftp_file, target_directory
        )
    except Exception as e:
        raise RuntimeError(
            f"Failed to download {most_recent_ftp_file} from FTP: {e}"
        ) from e

    # Upload the downloaded file to S3
    try:
        s3_store.upload_file(download_location, s3_bucket, s3_key)
        print(f"Successfully uploaded {most_recent_ftp_file} to {s3_bucket}/{s3_key}")
    except Exception as e:
        raise RuntimeError(f"Failed to upload {most_recent_ftp_file} to S3: {e}") from e

    # list what's in s3 and get the file with the highest date to send downstream
    s3_object = get_most_recent_valid_file(
        [key.split("/")[-1] for key in list_s3_keys(s3_bucket, s3_prefix)]
    )

    return f"s3://{s3_bucket}/{s3_prefix}/{s3_object}"


def handler(
    event: EbscoAdapterTriggerEvent, config: EbscoAdapterTriggerConfig
) -> EbscoAdapterLoaderEvent:
    print(f"Running handler with config: {config}")
    print(f"Processing event: {event}")

    ftp_server = get_ssm_parameter(f"{ssm_param_prefix}/ftp_server")
    ftp_username = get_ssm_parameter(f"{ssm_param_prefix}/ftp_username")
    ftp_password = get_ssm_parameter(f"{ssm_param_prefix}/ftp_password")
    ftp_remote_dir = get_ssm_parameter(f"{ssm_param_prefix}/ftp_remote_dir")

    with (
        EbscoFtp(ftp_server, ftp_username, ftp_password, ftp_remote_dir) as ebsco_ftp,
        tempfile.TemporaryDirectory() as temp_dir,
    ):
        s3_location = sync_files(
            ebsco_ftp=ebsco_ftp,
            target_directory=temp_dir,
            s3_bucket=s3_bucket,
            s3_prefix=ftp_s3_prefix,
        )

    # generate a job_id based on the schedule time, using an iso8601 format like 20210701T1300
    # job_id = datetime.fromisoformat(event.time.replace("Z", "+00:00")).strftime(
    #     "%Y%m%dT%H%M"
    # )
    print(f"Sending S3 location downstream: {s3_location}")
    return EbscoAdapterLoaderEvent(file_location=s3_location)  # add job_id back later


def lambda_handler(event: EbscoAdapterTriggerEvent, context: Any) -> dict[str, Any]:
    return handler(
        EbscoAdapterTriggerEvent.model_validate(event), EbscoAdapterTriggerConfig()
    ).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="Process XML file with EBSCO adapter")
    parser.add_argument(
        "--local",
        action="store_true",
        help="Run locally -writes to /dev S3 prefix",
    )

    args = parser.parse_args()

    event = EbscoAdapterTriggerEvent(time=datetime.now().strftime("%Y%m%dT%H%M"))
    config = EbscoAdapterTriggerConfig(is_local=args.local)

    handler(event=event, config=config)


if __name__ == "__main__":
    """Entry point for the trigger script"""
    print("Running local handler...")
    local_handler()
