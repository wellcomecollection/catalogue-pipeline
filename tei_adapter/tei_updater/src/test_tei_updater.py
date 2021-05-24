# -*- encoding: utf-8 -*-


import json
import mock
import os
import pytest
import requests

from betamax import Betamax
import boto3
from moto import mock_s3

from tei_updater import main

pytest_plugins = "catalogue_aws_fixtures"

with Betamax.configure() as config:
    config.cassette_library_dir = "."

@pytest.fixture
def session():
    session = requests.Session()
    with Betamax(session) as vcr:
        vcr.use_cassette("test_tei_updater")
        yield session

@mock_s3
def test_tree_does_not_exist(mock_sns_client,test_topic_arn, get_test_topic_messages, session):
    bucket = "bukkit"
    key = "tree.json"
    mock_s3_client = boto3.client("s3", region_name="us-east-1")
    mock_s3_client.create_bucket(Bucket=bucket)
    with mock.patch.dict(os.environ, {"TOPIC_ARN": test_topic_arn,
                                      "BUCKET_NAME": bucket,
                                      "TREE_FILE_KEY": key,
                                      "GITHUB_API_URL": "https://api.github.com/repos/wellcomecollection/wellcome-collection-tei/git/trees/master?recursive=true"}):
        main({}, s3_client=mock_s3_client, sns_client=mock_sns_client, session=session)
    messages = get_test_topic_messages()
    assert len(list(messages)) == 653
    content_object = mock_s3_client.get_object(Bucket=bucket,Key=key)
    body = content_object["Body"].read()
    saved_tree = json.loads(body)
    assert len(saved_tree.keys()) == 653

@mock_s3
def test_changes_to_old_tree_sent(mock_sns_client,test_topic_arn, get_test_topic_messages, session):
    bucket = "bukkit"
    key = "tree.json"
    mock_s3_client = boto3.client("s3", region_name="us-east-1")
    mock_s3_client.create_bucket(Bucket=bucket)

    with open('src/tei_tree.json','rb') as f:
        mock_s3_client.put_object(Bucket=bucket, Key=key, Body=f)
    with mock.patch.dict(os.environ, {"TOPIC_ARN": test_topic_arn,
                                      "BUCKET_NAME": bucket,
                                      "TREE_FILE_KEY": key,
                                      "GITHUB_API_URL": "https://api.github.com/repos/wellcomecollection/wellcome-collection-tei/git/trees/master?recursive=true"}):
        main({}, s3_client=mock_s3_client, sns_client=mock_sns_client, session=session)
    messages = get_test_topic_messages()
    assert len(list(messages)) == 2
    content_object = mock_s3_client.get_object(Bucket=bucket,Key=key)
    body = content_object["Body"].read()
    saved_tree = json.loads(body)
    assert len(saved_tree.keys()) == 653

def test_truncated_tree_results_in_error():
    assert False == True

def test_more_keys_in_diff_is_error():
    assert False == True
