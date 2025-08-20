import json

import boto3


def record_processed_file(
    job_id: str, file_location: str, changeset_id: str | None
) -> dict:
    """
    Record the file as processed
    Args:
        job_id: state machine execution id
        file_location: S3 path of the processed file
        changeset_id: identifies items added or updated in the current execution
    """
    s3_client = boto3.client("s3")

    path_parts = file_location.split("/")
    bucket_name = path_parts[2]
    key = f"{'/'.join(path_parts[3:])}.loaded.json"

    record = {"job_id": job_id, "changeset_id": changeset_id}

    s3_client.put_object(
        Bucket=bucket_name,
        Key=key,
        Body=json.dumps(record),
        ContentType="application/json",
    )

    return record


def is_file_already_processed(bucket: str, key: str) -> bool:
    """
    Check if a file has already been processed, ie. has a corresponding .loaded.json file
    Args:
        bucket: s3 bucket name
        key: s3 object key
    Returns:
        True if the file has already been processed, False otherwise
    """
    s3_client = boto3.client("s3")

    try:
        s3_client.head_object(Bucket=bucket, Key=f"{key}.loaded.json")
        return True
    except Exception:
        return False
