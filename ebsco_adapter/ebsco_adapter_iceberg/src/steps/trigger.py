"""Trigger step for the EBSCO adapter.

Fetches the latest valid MARC XML file from the source FTP server, uploads it
to S3 if not already present, and emits a loader event (job-scoped) pointing to
the chosen S3 object.
"""

import argparse
import re
import tempfile
from datetime import datetime
from typing import Any

import smart_open
from pydantic import BaseModel

from config import (
    FTP_S3_PREFIX,
    S3_BUCKET,
    SSM_PARAM_PREFIX,
)
from ebsco_ftp import EbscoFtp
from models.step_events import (
    EbscoAdapterLoaderEvent,
    EbscoAdapterTriggerEvent,
)
from utils.aws import get_ssm_parameter, list_s3_keys

"""Trigger step: now only responsible for syncing latest source file to S3.

Previously this step also looked up prior processing metadata and threaded a
``changeset_id`` downstream; that responsibility has been moved to the loader
which can now independently decide whether to short‑circuit.
"""

# No tracking imports needed here anymore.


class EbscoAdapterTriggerConfig(BaseModel):
    is_local: bool = False


class EventBridgeScheduledEvent(BaseModel):
    time: str  # original EventBridge schedule event time


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

    existing_keys = list_s3_keys(s3_bucket, s3_prefix)
    existing_filenames = {key.split("/")[-1] for key in existing_keys}

    # Check if the file already exists in S3
    try:
        s3_store.head_object(Bucket=s3_bucket, Key=s3_key)
        print(f"File {most_recent_ftp_file} already exists in S3. No need to download.")
        most_recent_s3_object = get_most_recent_valid_file(
            [key.split("/")[-1] for key in existing_keys]
        )
        return f"s3://{s3_bucket}/{s3_prefix}/{most_recent_s3_object}"
    except Exception as e:  # noqa: BLE001 - broad for fallback behaviour
        if "NoSuchKey" in str(e):
            print(f"{most_recent_ftp_file} not found in S3. Will download and upload.")
        else:
            print(
                f"Error checking S3 (head_object) for {most_recent_ftp_file}: {e}. Proceeding to download."
            )

    # Need to retrieve + upload the latest FTP file
    try:
        download_location = ebsco_ftp.download_file(
            most_recent_ftp_file, target_directory
        )
    except Exception as e:  # noqa: BLE001
        raise RuntimeError(
            f"Failed to download {most_recent_ftp_file} from FTP: {e}"
        ) from e

    destination_uri = f"s3://{s3_bucket}/{s3_prefix}/{most_recent_ftp_file}"
    try:
        # Stream local file -> S3 using smart_open for consistency across steps
        with (
            open(download_location, "rb") as src,
            smart_open.open(destination_uri, "wb") as dst,
        ):
            dst.write(src.read())
        print(f"Successfully uploaded {most_recent_ftp_file} to {destination_uri}")
    except Exception as e:  # noqa: BLE001
        raise RuntimeError(
            f"Failed to upload {most_recent_ftp_file} to {destination_uri}: {e}"
        ) from e

    # Refresh S3 key listing to determine most recent object to pass downstream
    refreshed_keys = list_s3_keys(s3_bucket, s3_prefix)
    most_recent_s3_object = get_most_recent_valid_file(
        [key.split("/")[-1] for key in list_s3_keys(s3_bucket, s3_prefix)]
    )

    return f"s3://{s3_bucket}/{s3_prefix}/{most_recent_s3_object}"


def handler(
    event: EbscoAdapterTriggerEvent, config: EbscoAdapterTriggerConfig
) -> EbscoAdapterLoaderEvent:
    print(f"Running handler with config: {config}")
    print(f"Processing event: {event}")

    job_id = event.job_id

    ftp_server = get_ssm_parameter(f"{SSM_PARAM_PREFIX}/ftp_server")
    ftp_username = get_ssm_parameter(f"{SSM_PARAM_PREFIX}/ftp_username")
    ftp_password = get_ssm_parameter(f"{SSM_PARAM_PREFIX}/ftp_password")
    ftp_remote_dir = get_ssm_parameter(f"{SSM_PARAM_PREFIX}/ftp_remote_dir")

    with (
        EbscoFtp(ftp_server, ftp_username, ftp_password, ftp_remote_dir) as ebsco_ftp,
        tempfile.TemporaryDirectory() as temp_dir,
    ):
        s3_location = sync_files(
            ebsco_ftp=ebsco_ftp,
            target_directory=temp_dir,
            s3_bucket=S3_BUCKET,
            s3_prefix=FTP_S3_PREFIX,
        )

    print(f"Sending S3 location downstream: {s3_location}")
    # We no longer look up prior processing here; loader is responsible for
    # determining if this source file has already been loaded (and can then
    # short-circuit).
    return EbscoAdapterLoaderEvent(job_id=job_id, file_location=s3_location)


def lambda_handler(event: EventBridgeScheduledEvent, context: Any) -> dict[str, Any]:
    eventbridge_event = EventBridgeScheduledEvent.model_validate(event)

    # Convert external scheduled event into internal trigger event with job_id
    job_id = datetime.fromisoformat(
        eventbridge_event.time.replace("Z", "+00:00")
    ).strftime("%Y%m%dT%H%M")
    internal_event = EbscoAdapterTriggerEvent(job_id=job_id)
    return handler(internal_event, EbscoAdapterTriggerConfig()).model_dump()


def local_handler() -> None:
    parser = argparse.ArgumentParser(description="Process XML file with EBSCO adapter")
    parser.add_argument(
        "--local",
        action="store_true",
        help="Run locally -writes to /dev S3 prefix",
    )
    parser.add_argument(
        "--job-id",
        type=str,
        required=False,
        help="Optional job id (defaults to current time if omitted)",
    )

    args = parser.parse_args()

    job_id = args.job_id or datetime.now().strftime("%Y%m%dT%H%M")

    event = EbscoAdapterTriggerEvent(job_id=job_id)
    config = EbscoAdapterTriggerConfig(is_local=args.local)

    handler(event=event, config=config)


if __name__ == "__main__":
    """Entry point for the trigger script"""
    print("Running local handler...")
    local_handler()
