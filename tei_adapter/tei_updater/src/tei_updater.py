# -*- encoding: utf-8 -*-
import json
import os
import requests

import boto3
from botocore.exceptions import ClientError
from deepdiff import DeepDiff

from wellcome_aws_utils import sns_utils
from wellcome_aws_utils.lambda_utils import log_on_error


def get_stored_tree(s3, bucket, key):
    try:
        content_object = s3.get_object(Bucket=bucket, Key=key)
        body = content_object["Body"].read()
        old_tree = json.loads(body)
        return old_tree
    except ClientError as ex:
        if ex.response["Error"]["Code"] == "NoSuchKey":
            return None
        else:
            raise ex


def get_new_tree(url, session=None):
    session = session or requests.Session()
    response = session.get(url)
    response.raise_for_status()
    new_tree = {}
    response_tree = response.json()
    assert response_tree["truncated"] is False
    for entry in response_tree["tree"]:
        if entry["type"] == "blob":
            new_tree[entry["path"]] = {"sha": entry["sha"], "url": entry["url"]}
    return new_tree


def get_path_from_diff(deep_diff_path):
    return deep_diff_path.replace("root['", "").replace("']", "")


def diff_trees(old_tree, new_tree):
    diff = DeepDiff(old_tree, new_tree, view="tree")
    values_changed = diff.pop("values_changed", [])
    items_added = diff.pop("dictionary_item_added", [])
    items_removed = diff.pop("dictionary_item_removed", [])

    # assert that the diff only contains the three keys above
    assert len(diff.keys()) == 0

    messages = []

    if values_changed:
        paths_changed = {
            get_path_from_diff(changed.up.path()) for changed in values_changed
        }
        messages += [
            {"path": path, "url": new_tree[path]["url"]} for path in paths_changed
        ]
    if items_added:
        messages += [
            {
                "path": get_path_from_diff(added.path()),
                "url": new_tree[get_path_from_diff(added.path())]["url"],
            }
            for added in items_added
        ]
    if items_removed:
        messages += [
            {"path": get_path_from_diff(removed.path()), "deleted": True}
            for removed in items_removed
        ]
    return messages


@log_on_error
def main(event, _ctxt=None, s3_client=None, sns_client=None, session=None):
    topic_arn = os.environ["TOPIC_ARN"]
    bucket_name = os.environ["BUCKET_NAME"]
    key = os.environ["TREE_FILE_KEY"]
    github_api_url = os.environ["GITHUB_API_URL"]

    s3_client = s3_client or boto3.client("s3")
    sns_client = sns_client or boto3.client("sns")

    old_tree = get_stored_tree(s3_client, bucket_name, key)
    new_tree = get_new_tree(github_api_url, session)

    if old_tree:
        messages = diff_trees(old_tree, new_tree)
    else:
        messages = [
            {"path": path, "url": entry["url"]} for path, entry in new_tree.items()
        ]

    for message in messages:
        sns_utils.publish_sns_message(
            sns_client=sns_client,
            topic_arn=topic_arn,
            message=message,
            subject="source: tei_tree_updater.main",
        )
    new_tree_json = json.dumps(new_tree).encode("UTF-8")
    s3_client.put_object(Body=(bytes(new_tree_json)), Bucket=bucket_name, Key=key)
