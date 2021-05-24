# -*- encoding: utf-8 -*-


import json
import mock
import os
import pytest
import requests

from betamax import Betamax
import boto3
from botocore.exceptions import ClientError
from moto import mock_s3

from tei_updater import main
from tei_updater import diff_trees

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


@mock.patch("tei_updater.requests.get")
def test_truncated_tree_results_in_error(mock_get, mock_sns_client,test_topic_arn, get_test_topic_messages):
    mock_get.return_value.ok = True
    mock_get.return_value.json.return_value = {"tree":[],"truncated": True}
    bucket = "bukkit"
    key = "tree.json"
    mock_s3_client = boto3.client("s3", region_name="us-east-1")
    with mock.patch.dict(os.environ, {"TOPIC_ARN": test_topic_arn,
                                      "BUCKET_NAME": bucket,
                                      "TREE_FILE_KEY": key,
                                      "GITHUB_API_URL": "https://api.github.com/repos/wellcomecollection/wellcome-collection-tei/git/trees/master?recursive=true"}):

        with pytest.raises(Exception):
            main({}, s3_client=mock_s3_client, sns_client=mock_sns_client)
    messages = get_test_topic_messages()
    assert len(list(messages)) == 0
    with pytest.raises(ClientError) as e:
        mock_s3_client.get_object(Bucket=bucket,Key=key)
        assert e.response['Error']['Code'] == 'NoSuchKey'


def test_elements_added_changed_deleted_are_returned():
    old_tree = {"filea": {'sha': 'ababababa', 'url': 'http://filea'},
                "fileb": {'sha': 'bfvnwhgvdf', 'url': 'http://fileb'} ,
                "filec":{'sha': 'bgfbhsg', 'url': 'http://filec'}}
    new_tree = {"fileb": {'sha': 'dgfhkjgew', 'url': 'http://filebb'} ,
                "filec":{'sha': 'bgfbhsg', 'url': 'http://filec'},
                "filed":{'sha': 'dkgef', 'url': 'http://filed'},
                }
    diffs = diff_trees(old_tree, new_tree)
    expected_diffs = [
        {'path': "fileb", 'url':'http://filebb'},
        {'path': "filed", 'url':'http://filed'},
        {'path': "filea", 'deleted': True}
    ]
    assert diffs == expected_diffs


def test_more_types_of_diff_is_error():
    old_tree = {"filea": {'sha': 'ababababa', 'url': 'http://filea'},
                "fileb": {'sha': 'bfvnwhgvdf', 'url': 'http://fileb'} ,
                "filec":{'sha': 'bgfbhsg', 'url': 'http://filec'}}
    # filea.url has changes type so it will be returned under type_changed which we don't expect
    new_tree =  {"filea": {'sha': 'ababababa', 'url': 2},
                 "fileb": {'sha': 'bfvnwhgvdf', 'url': 'http://fileb'} ,
                 "filec":{'sha': 'bgfbhsg', 'url': 'http://filec'}}
    with pytest.raises(AssertionError):
        diff_trees(old_tree, new_tree)

