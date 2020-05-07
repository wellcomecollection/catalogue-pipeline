import os

import boto3
from botocore.exceptions import ClientError

from .logging import get_logger

logger = get_logger(__name__)


def get_s3_client():
    try:
        s3_client = boto3.client("s3")
    except ClientError as e:
        logger.error(f"Failed to create s3 client: {e}")
        raise e
    return s3_client


def download_object_from_s3(bucket_name, object_key, file_name=None):
    s3_client = get_s3_client()
    s3_client.download_file(
        Bucket=bucket_name,
        Key=object_key,
        Filename=(file_name or os.path.basename(object_key)),
    )
