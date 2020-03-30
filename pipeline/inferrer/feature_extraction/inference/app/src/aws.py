import os
from io import BytesIO

import boto3
from botocore.exceptions import ClientError

from .logging import get_logstash_logger

logger = get_logstash_logger(__name__)


def get_s3_client(profile_name=None):
    try:
        if profile_name:
            session = boto3.session.Session(
                profile_name=profile_name, region_name='eu-west-1'
            )
            s3_client = session.client('s3')
        else:
            s3_client = boto3.client('s3')
    except ClientError as e:
        logger.error(f'Failed to create s3 client: {e}')
        raise e
    return s3_client


def download_object_from_s3(object_key, bucket_name, profile_name=None):
    s3_client = get_s3_client(profile_name)
    s3_client.download_file(
        Bucket=bucket_name,
        Key=object_key,
        Filename=os.path.basename(object_key)
    )
