import argparse
import os
import tempfile
from datetime import datetime
from typing import Any

import boto3
from pydantic import BaseModel

from ebsco_ftp import EbscoFtp
from steps.loader import EbscoAdapterLoaderEvent
from utils.aws import get_ssm_parameter

ssm_param_prefix = "/catalogue_pipeline/ebsco_adapter"
s3_bucket = os.environ.get("S3_BUCKET", "wellcomecollection-platform-ebsco-adapter")
s3_prefix = os.environ.get("S3_PREFIX", "dev")
ftp_s3_prefix = os.path.join(s3_prefix, "ftp_v2")


class EbscoAdapterTriggerConfig(BaseModel):
    is_local: bool = False


class EventBridgeTriggerEvent(BaseModel):
    time: str


def validate_ftp_filename(filename: str) -> bool:
    """Validate that the filename matches the expected pattern"""
    if not filename.startswith("ebz-s7451719-") or not filename.endswith(".xml"):
        return False

    try:
        # Extract the date part (third component when split by '-')
        date_part = filename.split("-")[2]
        # Date part must be exactly 8 digits (YYYYMMDD)
        if len(date_part) != 8 or not date_part.isdigit():
            return False
        # Try to parse the date to validate it's a valid date
        datetime.strptime(date_part, "%Y%m%d")
        return True
    except (ValueError, IndexError):
        return False


def get_most_recent_S3_object(s3_client, bucket: str, prefix: str) -> str: # type: ignore
    response = s3_client.list_objects_v2(Bucket=bucket, Prefix=prefix)

    s3_objects = []
    for obj in response.get("Contents", []):
        key: str = obj["Key"]
        # get the part of the S3 prefix after the last slash and split by '-'
        date_part = key[key.rfind("/") + 1 :].split("-")[2]
        s3_objects.append((int(date_part), key))

    if not s3_objects:
        raise ValueError("No objects found in S3")

    most_recent = max(s3_objects, key=lambda x: x[0])
    return most_recent[1]  # Return the key


def sync_files(
    ebsco_ftp: EbscoFtp, target_directory: str, s3_bucket: str, s3_prefix: str
) -> str:
    valid_ftp_files = ebsco_ftp.list_files(validate_ftp_filename)

    if not valid_ftp_files:
        raise ValueError("No XML files found on FTP server")

    # valid files are in the format ebz-s7451719-20240322-1.xml
    # the third part is the date
    most_recent_ftp_file = sorted(
        valid_ftp_files, key=lambda x: x.split("-")[2], reverse=True
    )[0]
    print(f"Most recent ftp file: {most_recent_ftp_file}")

    s3_store = boto3.client("s3")
    s3_key = f"{s3_prefix}/{most_recent_ftp_file}"

    # Check if the file already exists in S3
    try:
        s3_store.head_object(Bucket=s3_bucket, Key=s3_key)
        print(f"File {most_recent_ftp_file} already exists in S3. No need to download.")
        # we return the S3 location of the most recent file
        return f"s3://{s3_bucket}/{get_most_recent_S3_object(s3_store, s3_bucket, s3_prefix)}"
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
    obj_key = get_most_recent_S3_object(s3_store, s3_bucket, s3_prefix)

    return f"s3://{s3_bucket}/{obj_key}"


def handler(
    event: EventBridgeTriggerEvent, config: EbscoAdapterTriggerConfig
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
            s3_prefix=ftp_s3_prefix if not config.is_local else "dev",
        )

    # generate a job_id based on the schedule time, using an iso8601 format like 20210701T1300
    # job_id = datetime.fromisoformat(event.time.replace("Z", "+00:00")).strftime(
    #     "%Y%m%dT%H%M"
    # )

    return EbscoAdapterLoaderEvent(s3_location=s3_location) # add job_id back later


def lambda_handler(event: EventBridgeTriggerEvent, context: Any) -> dict[str, Any]:
    return handler(
        EventBridgeTriggerEvent.model_validate(event), EbscoAdapterTriggerConfig()
    ).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="Process XML file with EBSCO adapter")
    parser.add_argument(
        "--local",
        action="store_true",
        help="Run locally -writes to /dev S3 prefix",
    )

    args = parser.parse_args()

    event = EventBridgeTriggerEvent(time=datetime.now().strftime("%Y%m%dT%H%M"))
    config = EbscoAdapterTriggerConfig(is_local=args.local)

    handler(event=event, config=config)


if __name__ == "__main__":
    """Entry point for the trigger script"""
    print("Running local handler...")
    local_handler()
