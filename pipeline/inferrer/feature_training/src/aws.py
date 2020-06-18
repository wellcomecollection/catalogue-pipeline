import boto3
import os
import requests
from botocore.exceptions import ClientError
from .logging import get_logger

logger = get_logger(__name__)


def get_ecs_container_metadata():
    endpoint = os.environ["ECS_CONTAINER_METADATA_URI_V4"]
    resp = requests.get(endpoint)
    resp.raise_for_status()
    return resp.json()


def get_s3_client():
    try:
        s3_client = boto3.client("s3")
    except ClientError as e:
        logger.info(f"Failed to create s3 client: {e}")
        raise e
    return s3_client


def put_object_to_s3(binary_object, key, bucket_name):
    s3_client = get_s3_client()
    logger.info("Uploading object to S3 bucket %r with key %r...", bucket_name, key)
    s3_client.put_object(Bucket=bucket_name, Key=key, Body=binary_object)
    logger.info("Uploaded object to S3.")


def put_ssm_parameter(path, value, description):
    ssm_client = boto3.client("ssm")

    logger.info("Updating SSM path %r to %r...", path, value)
    ssm_client.put_parameter(
        Name=path, Description=description, Value=value, Type="String", Overwrite=True
    )
    logger.info("Updated SSM.")
