"""
This file contains some helper functions for updating the data in the
Miro VHS, e.g. to suppress images or override the licence.
"""

import datetime
import json

import boto3

from _common import get_session


SESSION = get_session(role_arn="arn:aws:iam::760097843905:role/platform-developer")
DYNAMO_CLIENT = SESSION.resource("dynamodb").meta.client

TABLE_NAME = "vhs-sourcedata-miro"


def _read_from_s3(bucket, key):
    s3 = SESSION.client("s3")
    obj = s3.get_object(Bucket=bucket, Key=key)
    return obj["Body"].read()


def _get_reindexer_topic_arn():
    statefile_body = _read_from_s3(
        bucket="wellcomecollection-platform-infra",
        key="terraform/catalogue/reindexer.tfstate",
    )

    # The structure of the interesting bits of the statefile is:
    #
    #   {
    #       ...
    #       "outputs": {
    #          "name_of_output": {
    #              "value": "1234567890x",
    #              ...
    #          },
    #          ...
    #      }
    #   }
    #
    statefile_data = json.loads(statefile_body)
    outputs = statefile_data["outputs"]
    return outputs["topic_arn"]["value"]


def _request_reindex_for(miro_id):
    sns_client = SESSION.client("sns")

    message = {
        "jobConfigId": "miro--catalogue_miro_updates",
        "parameters": {"ids": [miro_id], "type": "SpecificReindexParameters"},
    }

    sns_client.publish(
        TopicArn=_get_reindexer_topic_arn(),
        Message=json.dumps(message),
    )


def _get_timestamp():
    # DynamoDB formats timestamps as milliseconds past the epoch
    return int(datetime.datetime.now().timestamp() * 1000)


def _get_user():
    """
    Returns the original role ARN.
    e.g. at Wellcome we have a base role, but then we assume roles into different
    accounts.  This returns the ARN of the base role.
    """
    client = boto3.client("sts")
    return client.get_caller_identity()["Arn"]


def _set_image_availability(*, miro_id, message: str, is_available: bool):
    item = DYNAMO_CLIENT.get_item(TableName=TABLE_NAME, Key={"id": miro_id})["Item"]

    new_event = {
        "description": "Change isClearedForCatalogueAPI from %r to %r"
        % (item["isClearedForCatalogueAPI"], is_available),
        "message": message,
        "date": _get_timestamp(),
        "user": _get_user(),
    }

    try:
        events = item["events"] + [new_event]
    except KeyError:
        events = [new_event]

    DYNAMO_CLIENT.update_item(
        TableName=TABLE_NAME,
        Key={"id": miro_id},
        UpdateExpression="SET #version = :newVersion, #events = :events, #isClearedForCatalogueAPI = :is_available",
        ConditionExpression="#version = :oldVersion",
        ExpressionAttributeNames={
            "#version": "version",
            "#events": "events",
            "#isClearedForCatalogueAPI": "isClearedForCatalogueAPI",
        },
        ExpressionAttributeValues={
            ":oldVersion": item["version"],
            ":newVersion": item["version"] + 1,
            ":events": events,
            ":is_available": is_available,
        },
    )

    _request_reindex_for(miro_id)


def make_image_available(*, miro_id, message: str):
    """
    Make a Miro image available on wellcomecollection.org.
    """
    _set_image_availability(miro_id=miro_id, message=message, is_available=True)


def suppress_image(*, miro_id, message: str):
    """
    Hide a Miro image from wellcomecollection.org.
    """
    _set_image_availability(miro_id=miro_id, message=message, is_available=False)


def _set_overrides(*, miro_id, message: str, override_key: str, override_value: str):
    item = DYNAMO_CLIENT.get_item(TableName=TABLE_NAME, Key={"id": miro_id})["Item"]

    new_event = {
        "description": "Change overrides.%s from %r to %r"
        % (override_key, item.get("overrides", {}).get(override_key), override_value),
        "message": message,
        "date": _get_timestamp(),
        "user": _get_user(),
    }

    overrides = item.get("overrides", {})
    overrides[override_key] = override_value

    try:
        events = item["events"] + [new_event]
    except KeyError:
        events = [new_event]

    DYNAMO_CLIENT.update_item(
        TableName=TABLE_NAME,
        Key={"id": miro_id},
        UpdateExpression="SET #version = :newVersion, #events = :events, #overrides = :overrides",
        ConditionExpression="#version = :oldVersion",
        ExpressionAttributeNames={
            "#version": "version",
            "#events": "events",
            "#overrides": "overrides",
        },
        ExpressionAttributeValues={
            ":oldVersion": item["version"],
            ":newVersion": item["version"] + 1,
            ":events": events,
            ":overrides": overrides,
        },
    )

    _request_reindex_for(miro_id)


def _remove_override(*, miro_id, message: str, override_key: str):
    item = DYNAMO_CLIENT.get_item(TableName=TABLE_NAME, Key={"id": miro_id})["Item"]

    new_event = {
        "description": "Remove overrides.%s (previously %r)"
        % (override_key, item.get("overrides", {}).get(override_key)),
        "message": message,
        "date": _get_timestamp(),
        "user": _get_user(),
    }

    overrides = item.get("overrides", {})

    try:
        del overrides[override_key]
    except KeyError:
        pass

    try:
        events = item["events"] + [new_event]
    except KeyError:
        events = [new_event]

    DYNAMO_CLIENT.update_item(
        TableName=TABLE_NAME,
        Key={"id": miro_id},
        UpdateExpression="SET #version = :newVersion, #events = :events, #overrides = :overrides",
        ConditionExpression="#version = :oldVersion",
        ExpressionAttributeNames={
            "#version": "version",
            "#events": "events",
            "#overrides": "overrides",
        },
        ExpressionAttributeValues={
            ":oldVersion": item["version"],
            ":newVersion": item["version"] + 1,
            ":events": events,
            ":overrides": overrides,
        },
    )

    _request_reindex_for(miro_id)


def set_license_override(*, miro_id: str, license_code: str, message: str):
    """
    Set the license of a Miro image on wellcomecollection.org.
    """
    _set_overrides(
        miro_id=miro_id,
        message=message,
        override_key="license",
        override_value=license_code,
    )


def remove_license_override(*, miro_id: str, message: str):
    _remove_override(miro_id=miro_id, message=message, override_key="license")
