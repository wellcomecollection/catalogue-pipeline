"""
This file contains some helper functions for updating the data in the
Miro VHS, e.g. to suppress images or override the licence.
"""

import datetime
import functools
import json
import sys
import re
import os

import boto3
import httpx

from botocore.exceptions import ClientError

from _common import (
    git,
    get_api_es_client,
    get_ingestor_es_client,
    get_secret_string,
    get_session,
    get_date_from_index_name,
    get_dynamodb_items,
)

SESSION = get_session(role_arn="arn:aws:iam::760097843905:role/platform-developer")
DYNAMO_CLIENT = SESSION.resource("dynamodb").meta.client

TABLE_NAME = "vhs-sourcedata-miro"


@functools.lru_cache()
def api_es_client(date):
    return get_api_es_client(date)


@functools.lru_cache()
def work_ingestor_es_client(date):
    return get_ingestor_es_client(date=date, doc_type="work")


@functools.lru_cache()
def image_ingestor_es_client(date):
    return get_ingestor_es_client(date=date, doc_type="image")


@functools.lru_cache()
def dlcs_api_client():
    digirati_session = get_session(
        role_arn="arn:aws:iam::653428163053:role/digirati-developer"
    )

    api_key = get_secret_string(
        digirati_session, secret_id="iiif-builder/common/dlcs-apikey"
    )
    api_secret = get_secret_string(
        digirati_session, secret_id="iiif-builder/common/dlcs-apisecret"
    )

    return httpx.Client(auth=(api_key, api_secret))


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


def has_subscriptions(sns_client, *, topic_arn):
    """
    Returns True if a topic ARN has any subscriptions (e.g. an SQS queue), False otherwise.
    """
    resp = sns_client.list_subscriptions_by_topic(TopicArn=topic_arn)

    return bool(resp["Subscriptions"])


def _request_reindex_for(miro_id):
    sns_client = SESSION.client("sns")
    message = {
        "jobConfigId": "miro--catalogue_miro_updates",
        "parameters": {"ids": [miro_id], "type": "SpecificReindexParameters"},
    }

    sns_client.publish(TopicArn=_get_reindexer_topic_arn(), Message=json.dumps(message))


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


def _remove_image_from_elasticsearch(*, miro_id):
    search_templates_url = (
        "https://api.wellcomecollection.org/catalogue/v2/search-templates.json"
    )
    resp = httpx.get(search_templates_url)
    current_indexes = set(template["index"] for template in resp.json()["templates"])

    works_index = next(index for index in current_indexes if index.startswith("works-"))
    images_index = next(
        index for index in current_indexes if index.startswith("images-")
    )
    pipeline_date = get_date_from_index_name(works_index)

    # Remove the work from the works index
    works_resp = api_es_client(pipeline_date).search(
        index=works_index,
        body={"query": {"term": {"query.identifiers.value": miro_id}}},
    )

    try:
        work = next(
            hit
            for hit in works_resp["hits"]["hits"]
            if hit["_source"]["debug"]["source"]["identifier"]["identifierType"]["id"]
            == "miro-image-number"
        )
    except StopIteration:
        print(f"Could not find a work for {miro_id} in {works_index}", file=sys.stderr)
        print(
            "It could be that the canonical work for this Miro ID is a Sierra work - that should be suppressed by collections information first",
            file=sys.stderr,
        )
        return
    else:
        work["_source"]["debug"]["deletedReason"] = {
            "info": "Miro: isClearedForCatalogueAPI = false",
            "type": "SuppressedFromSource",
        }
        work["_source"]["type"] = "Deleted"

        index_resp = work_ingestor_es_client(date=pipeline_date).index(
            index=works_index, body=work["_source"], id=work["_id"]
        )
        assert index_resp["result"] == "updated", index_resp

    images_resp = api_es_client(pipeline_date).search(
        index=images_index,
        body={
            "query": {"term": {"query.source.sourceIdentifier.value": miro_id}},
            "_source": "",
        },
    )

    try:
        image_id = images_resp["hits"]["hits"][0]["_id"]
    except IndexError:
        print(
            f"Could not find an image for {work['_id']} in {images_index}",
            file=sys.stderr,
        )
        return
    else:
        delete_resp = image_ingestor_es_client(date=pipeline_date).delete(
            index=images_index, id=image_id
        )
        assert delete_resp["result"] == "deleted", delete_resp


def _remove_image_from_dlcs(*, miro_id):
    # Wellcome = customer 2, Miro = space 8
    # See https://wellcome.slack.com/archives/CBT40CMKQ/p1621496639019200?thread_ts=1621495275.018100&cid=CBT40CMKQ
    resp = dlcs_api_client().delete(
        f"https://api.dlcs.io/customers/2/spaces/8/images/{miro_id}"
    )
    if resp.status_code == 404:
        print(
            f"Failed to delete image {miro_id} from DLCS, image not found!",
            file=sys.stderr,
        )
        return
    else:
        assert resp.status_code == 204, resp


def _remove_image_from_cloudfront(*, miro_id):
    cloudfront_client = SESSION.client("cloudfront")

    try:
        cloudfront_client.create_invalidation(
            DistributionId="E1KKXGJWOADM2A",  # IIIF APIs prod
            InvalidationBatch={
                "Paths": {"Quantity": 1, "Items": [f"/image/{miro_id}*"]},
                "CallerReference": f"{__file__} invalidating {miro_id}",
            },
        )
    except ClientError:
        print(f"Failed to invalidate {miro_id} from CloudFront", file=sys.stderr)


def suppress_image(*, miro_id, message: str):
    """
    Hide a Miro image from wellcomecollection.org.
    These operations must happen in a specific order: _set_image_availability first, as the DDB table is the source of truth for Miro images when building pipelines
    """
    _set_image_availability(miro_id=miro_id, message=message, is_available=False)

    _remove_image_from_elasticsearch(miro_id=miro_id)
    _remove_image_from_dlcs(miro_id=miro_id)
    _remove_image_from_cloudfront(miro_id=miro_id)


def unsuppress_image(*, miro_id: str, origin: str, message: str):
    """
    Reinstate a hidden Miro image
    """
    sns_client = SESSION.client("sns")
    topic_arn = _get_reindexer_topic_arn()
    if not has_subscriptions(sns_client, topic_arn=topic_arn):
        print(
            "Nothing is listening to the reindexer, this action will not have the expected effect, aborting"
        )
        exit(1)

    # First, make the DDS record reflect that the image should be visible
    _set_image_availability(miro_id=miro_id, message=message, is_available=True)

    # Now the actual image must be registered on DLCS so that it can be seen
    register_on_dlcs(origin_url=origin, miro_id=miro_id)


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


def get_all_miro_suppression_events():
    for item in get_dynamodb_items(SESSION, TableName=TABLE_NAME):
        try:
            # Note: there are cases where the suppressed description was
            # added to DynamoDB after the image was initially suppressed,
            # so we need to catch both variants of this message.
            first_deletion = next(
                ev
                for ev in item.get("events", [])
                if ev["description"]
                in {
                    "Change isClearedForCatalogueAPI from True to False",
                    "Change isClearedForCatalogueAPI from False to False",
                }
            )
        except StopIteration:
            continue

        yield {
            "id": item["id"],
            "message": first_deletion["message"],
            "date": datetime.datetime.fromtimestamp(int(first_deletion["date"]) / 1000),
        }


def update_miro_image_suppressions_doc():
    print(
        "*** Creating a workflow_dispatch event for update_miro_suppressions_doc.yml in repo wellcomecollection/private ***"
    )

    # create a workflow_dispatch event to trigger the update_miro_suppressions_doc.yml workflow
    # the Github CLI is required
    os.system(
        f"gh workflow run update_miro_suppressions_doc.yml "
        f"--repo wellcomecollection/private "
        f"--field committer='{git('config', 'user.name')} <{git('config', 'user.email')}>'"
    )


def register_on_dlcs(origin_url, miro_id):
    dlcs_response = dlcs_api_client().post(
        f"https://api.dlcs.io/customers/2/queue/priority",
        json={
            "@type": "Collection",
            "member": [
                {
                    "space": "8",
                    "origin": origin_url,
                    "id": miro_id,
                    "mediaType": "image/jpeg",
                }
            ],
        },
    )
    # DLCS will process the above request asynchronously and it may take considerable time.
    # This is particularly true if it is already busy with something else.
    # The response contains details that will allow you to interrogate DLCS to
    # find out whether it has processed (or failed to process - e.g. there's a typo in your origin_url) your request.
    print(dlcs_response.text)


RE_MIRO_ID = re.compile("^[A-Z][0-9]{7}[A-Z]{0,4}[0-9]{0,2}$")


def is_valid_miro_id(maybe_miro_id: str):
    return RE_MIRO_ID.fullmatch(maybe_miro_id)
