import boto3


def _get_tracking_info(s3_uri: str) -> dict[str, str]:
    """
    Get the S3 bucket and key for the tracking file based on the original file's S3 URI.
    Returns:
        Dict with 'bucket', 'key' and 'filename' entries
    """
    path_parts = s3_uri.split("/")
    bucket_name = path_parts[2]
    # Get the prefix (everything between bucket and filename) and add tracking file
    prefix = "/".join(path_parts[3:-1])
    tracking_key = f"{prefix}/processed_files.txt"

    return {
        "bucket": bucket_name,
        "key": tracking_key,
        "filename": s3_uri.split("/")[-1],
    }


def _get_tracking_file_content(file_location: str) -> tuple[dict[str, str], str | None]:
    """
    Helper function to get tracking info and existing content.

    Returns:
        Tuple of (tracking_info, existing_content or None if file doesn't exist)
    """
    s3_client = boto3.client("s3")
    tracking_info = _get_tracking_info(file_location)

    bucket_name = tracking_info["bucket"]
    tracking_key = tracking_info["key"]

    try:
        response = s3_client.get_object(Bucket=bucket_name, Key=tracking_key)
        existing_content = response["Body"].read().decode("utf-8")
        return tracking_info, existing_content
    except s3_client.exceptions.NoSuchKey:
        return tracking_info, None


def record_processed_file(file_location: str) -> None:
    """
    Record the filename to an S3 tracking file
    Args:
        file_location: S3 path of the processed file
    """
    s3_client = boto3.client("s3")
    tracking_info, existing_content = _get_tracking_file_content(file_location)

    bucket_name = tracking_info["bucket"]
    tracking_key = tracking_info["key"]
    file_name = tracking_info["filename"]

    if existing_content is not None:
        new_content = existing_content.rstrip() + f"\n{file_name}"
    else:
        new_content = file_name

    s3_client.put_object(
        Bucket=bucket_name,
        Key=tracking_key,
        Body=new_content.encode("utf-8"),
        ContentType="text/plain",
    )


def is_file_already_processed(file_location: str) -> bool:
    """
    Check if a file has already been processed.
    Args:
        file_location: S3 path of the file to check
    Returns:
        True if the file has already been processed, False otherwise
    """
    tracking_info, existing_content = _get_tracking_file_content(file_location)

    if existing_content is None:
        # File doesn't exist, so nothing has been processed yet
        return False

    file_name = tracking_info["filename"]
    processed_files = existing_content.strip().split("\n")
    return file_name in processed_files
