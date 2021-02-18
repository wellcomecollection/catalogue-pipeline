# -*- encoding: utf-8 -*-

import boto3
import random
from moto import mock_s3
import pytest

from sierra_progress_reporter import get_matching_s3_keys


@pytest.fixture(scope="module")
def s3_client():
    with mock_s3():
        s3_client = boto3.client("s3", region_name="eu-west-1")
        yield s3_client


@pytest.fixture(scope="function")
def bucket(s3_client):
    bucket = "test-python-bucket-%d" % random.randint(0, 10000)
    s3_client.create_bucket(
        Bucket=bucket, CreateBucketConfiguration={"LocationConstraint": "eu-west-1"}
    )
    yield bucket


def test_get_matching_s3_keys(bucket, s3_client):
    keys = ["key%d" % i for i in range(250)]
    for k in keys:
        s3_client.put_object(Bucket=bucket, Key=k)

    result = get_matching_s3_keys(s3_client=s3_client, bucket=bucket, prefix="")
    assert set(list(result)) == set(keys)


def test_get_matching_s3_keys_filters_on_prefix(bucket, s3_client):
    keys = ["key%d" % i for i in range(250)]
    for k in keys:
        s3_client.put_object(Bucket=bucket, Key=k)

    other_keys = ["_key%d" % i for i in range(100)]
    for k in other_keys:
        s3_client.put_object(Bucket=bucket, Key=k)

    result = get_matching_s3_keys(s3_client=s3_client, bucket=bucket, prefix="key")
    assert set(list(result)) == set(keys)
