from typing import cast

import boto3


def get_ssm_parameter(parameter_name: str) -> str:
    """Returns an AWS SSM parameter string associated with a given name."""
    ssm_client = boto3.Session().client("ssm")
    response = ssm_client.get_parameter(Name=parameter_name, WithDecryption=True)
    return cast(str, response["Parameter"]["Value"])


def list_s3_keys(bucket: str, prefix: str) -> list[str]:
    """Lists all S3 keys in a given bucket and prefix."""
    s3_client = boto3.Session().client("s3")
    response = s3_client.list_objects_v2(Bucket=bucket, Prefix=prefix)
    keys = []
    for s3_obj in response.get("Contents", []):
        keys.append(s3_obj["Key"])
    return keys
