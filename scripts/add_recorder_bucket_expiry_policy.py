#!/usr/bin/env python
"""
The recorder S3 buckets fill up with stuff and then we stop using them.
Emptying them manually is slow (and tedious), and they clutter up Terraform output.

This script adds an Expiry policy to a bucket that deletes all objects after
one day, so we can quickly mark a bucket as "to be emptied".
"""

import sys

import boto3


sts_client = boto3.client("sts")


def get_aws_client(resource, *, role_arn):
    assumed_role_object = sts_client.assume_role(
        RoleArn=role_arn, RoleSessionName="AssumeRoleSession1"
    )
    credentials = assumed_role_object["Credentials"]
    return boto3.client(
        resource,
        aws_access_key_id=credentials["AccessKeyId"],
        aws_secret_access_key=credentials["SecretAccessKey"],
        aws_session_token=credentials["SessionToken"],
    )


if __name__ == "__main__":
    try:
        bucket_name = sys.argv[1]
    except IndexError:
        sys.exit(f"Usage: {__file__} <BUCKET_NAME>")

    if not bucket_name.endswith("-recorder"):
        sys.exit(
            f"Cowardly refusing to add an expiry policy to non-recorder bucket {bucket_name}"
        )

    s3 = get_aws_client("s3", role_arn="arn:aws:iam::760097843905:role/platform-admin")

    s3.put_bucket_lifecycle_configuration(
        Bucket=bucket_name,
        LifecycleConfiguration={
            "Rules": [
                {
                    "Expiration": {"Days": 1},
                    "ID": "cleanup-recorder-bucket",
                    "Prefix": "",
                    "Status": "Enabled",
                    "NoncurrentVersionExpiration": {"NoncurrentDays": 1},
                },
            ]
        },
        ExpectedBucketOwner="760097843905",
    )

    print(bucket_name)
