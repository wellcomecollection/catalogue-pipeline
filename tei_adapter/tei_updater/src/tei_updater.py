# -*- encoding: utf-8 -*-
import datetime
import dateutil.parser as parser
import decimal
import json
import logging
import os
import pytz
import requests

import boto3
from botocore.exceptions import ClientError
from deepdiff import DeepDiff


logger = logging.getLogger(__name__)

tzinfos = {tz: pytz.timezone(tz) for tz in pytz.all_timezones}


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
    datetime = parser.parse(response.headers["date"], tzinfos=tzinfos).astimezone(
        pytz.utc
    )
    # The tei id extractor needs to parse this into a java.time.Instant.
    # For _reasons_ parsing into Instant fails if there is an offset instead of Z
    time = datetime.isoformat().replace("+00:00", "Z")
    new_tree = {}
    response_tree = response.json()
    assert response_tree["truncated"] is False
    for entry in response_tree["tree"]:
        if entry["type"] == "blob":
            new_tree[entry["path"]] = {"sha": entry["sha"], "uri": entry["url"]}
    return new_tree, time


def get_path_from_diff(deep_diff_path):
    return deep_diff_path.replace("root['", "").replace("']", "")


def diff_trees(old_tree, new_tree, time):
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
            {"path": path, "uri": new_tree[path]["uri"], "timeModified": time}
            for path in paths_changed
        ]
    if items_added:
        messages += [
            {
                "path": get_path_from_diff(added.path()),
                "uri": new_tree[get_path_from_diff(added.path())]["uri"],
                "timeModified": time,
            }
            for added in items_added
        ]
    if items_removed:
        messages += [
            {"path": get_path_from_diff(removed.path()), "timeDeleted": time}
            for removed in items_removed
        ]
    return messages


def get_authenticated_session(github_token_secret):
    session = requests.Session()
    if github_token_secret:
        secrets = boto3.client("secretsmanager")
        github_token = secrets.get_secret_value(SecretId=github_token_secret)[
            "SecretString"
        ]
        session.headers.update({"Authorization": f"Bearer {github_token}"})
    return session


class EnhancedJSONEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, datetime.datetime):
            return obj.isoformat()

        if isinstance(obj, decimal.Decimal):
            if float(obj).is_integer():
                return int(obj)
            else:
                return float(obj)

        return json.JSONEncoder.default(self, obj)


def publish_sns_message(sns_client, topic_arn, message, subject="default-subject"):
    """
    Given a topic ARN and a series of key-value pairs, publish the key-value
    data to the SNS topic.
    """
    response = sns_client.publish(
        TopicArn=topic_arn,
        MessageStructure="json",
        Message=json.dumps({"default": json.dumps(message, cls=EnhancedJSONEncoder)}),
        Subject=subject,
    )

    if response["ResponseMetadata"]["HTTPStatusCode"] == 200:
        logger.debug("SNS: sent notification %s", response["MessageId"])
    else:
        raise RuntimeError(repr(response))

    return response


def main(event, _ctxt=None, s3_client=None, sns_client=None, session=None):
    topic_arn = os.environ["TOPIC_ARN"]
    bucket_name = os.environ["BUCKET_NAME"]
    key = os.environ["TREE_FILE_KEY"]
    github_api_url = os.environ["GITHUB_API_URL"]
    github_token_secret = os.environ.get("GITHUB_TOKEN_SECRET", None)

    s3_client = s3_client or boto3.client("s3")
    sns_client = sns_client or boto3.client("sns")
    authenticated_session = session or get_authenticated_session(github_token_secret)

    old_tree = get_stored_tree(s3_client, bucket_name, key)
    new_tree, time = get_new_tree(github_api_url, authenticated_session)

    if old_tree:
        messages = diff_trees(old_tree, new_tree, time)
    else:
        messages = [
            {"path": path, "uri": entry["uri"], "timeModified": time}
            for path, entry in new_tree.items()
        ]

    for message in messages:
        publish_sns_message(
            sns_client=sns_client,
            topic_arn=topic_arn,
            message=message,
            subject="source: tei_tree_updater.main",
        )

    new_tree_json = json.dumps(new_tree).encode("UTF-8")
    s3_client.put_object(Body=(bytes(new_tree_json)), Bucket=bucket_name, Key=key)
