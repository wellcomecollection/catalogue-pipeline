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
)

SESSION = get_session(role_arn="arn:aws:iam::760097843905:role/platform-developer")
DYNAMO_CLIENT = SESSION.resource("dynamodb").meta.client

MIRO_TABLE_NAME = "vhs-sourcedata-miro"

METS_TABLE_NAME = "mets-adapter-store-delta"


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


RE_MIRO_ID = re.compile("^[A-Z][0-9]{7}[A-Z]{0,4}[0-9]{0,2}$")

def is_valid_miro_id(maybe_miro_id: str):
    return RE_MIRO_ID.fullmatch(maybe_miro_id)


def _get_user():
    """
    Returns the original role ARN.
    e.g. at Wellcome we have a base role, but then we assume roles into different
    accounts.  This returns the ARN of the base role.
    """
    client = boto3.client("sts")
    return client.get_caller_identity()["Arn"]


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


def check_reindexer_listening(dry_run=False):
    sns_client = SESSION.client("sns")
    topic_arn = _get_reindexer_topic_arn()
    subscriptions = sns_client.list_subscriptions_by_topic(TopicArn=topic_arn)["Subscriptions"]
    if not subscriptions:
        if not dry_run:
            print(
                "Nothing is listening to the reindexer, this action will not have the expected effect, aborting"
            )
            return False
        else:
            print("No subscriptions found for reindexer topic. Please ensure reindexer subscribed before proceeding with the suppression.")
    else:
        print("Subscriptions found for reindexer topic:")
        for sub in subscriptions:
            print(f"{sub['SubscriptionArn']}")
    return bool(subscriptions)


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


def _get_current_pipeline_and_indices():
    search_templates_url = (
        "https://api.wellcomecollection.org/catalogue/v2/search-templates.json"
    )
    resp = httpx.get(search_templates_url)
    templates = resp.json()["templates"]
    current_indexes = set(template["index"] for template in templates)
    current_pipelines = set(template["pipeline"] for template in templates)

    if len(current_pipelines) != 1:
        raise RuntimeError(f"Found multiple pipelines: {current_pipelines}")

    works_indexes = [index for index in current_indexes if index.startswith("works-")]
    if len(works_indexes) != 1:
        raise RuntimeError(f"Found multiple works indices: {works_indexes}")

    images_indexes = [index for index in current_indexes if index.startswith("images-")]
    if len(images_indexes) != 1:
        raise RuntimeError(f"Found multiple images indices: {images_indexes}")

    return list(current_pipelines)[0], works_indexes[0], images_indexes[0]


def _get_vhs_sourcedata_miro_ddb_item(miro_id):
    try:
        item = DYNAMO_CLIENT.get_item(TableName=MIRO_TABLE_NAME, Key={"id": miro_id})["Item"]
    except KeyError:
        print(f"✗ Miro ID {miro_id} not found in DynamoDB table {MIRO_TABLE_NAME}", file=sys.stderr)
        return
    else:
        print(f"✓ Miro ID {miro_id} found in DynamoDB table {MIRO_TABLE_NAME}")
        return item

def _check_mets_adapter_store(work):
    identifiers = work["_source"]["query"]["identifiers.value"]
    
    for maybeMetsIdentifier in identifiers:
        try:
            item = DYNAMO_CLIENT.get_item(TableName=METS_TABLE_NAME, Key={"id": maybeMetsIdentifier})["Item"]
        except KeyError:
            continue
        else:
            print(f"✓ Identifier {maybeMetsIdentifier} found in DynamoDB table {METS_TABLE_NAME}\nThe image will need to be soft- or hard-deleted from the storage-service")
            return item

def _get_work_and_image(miro_id):
    pipeline_date, works_index, images_index = _get_current_pipeline_and_indices()

    # Check the work exists in the works index
    works_resp = api_es_client(pipeline_date).search(
        index=works_index,
        body={"query": {"term": {"query.identifiers.value": miro_id}}},
    )

    work = None
    try:
        work = next(
            hit
            for hit in works_resp["hits"]["hits"]
            if hit["_source"]["debug"]["source"]["identifier"]["identifierType"]["id"]
            == "miro-image-number"
        )
        print(f"✓ Work for {miro_id} found in {works_index}: {work['_id']}")
    except StopIteration:
        print(
            f"✗ Could not find a work for {miro_id} in {works_index}\n"
            "  It could be that the canonical work for this Miro ID is a Sierra work - that should be suppressed by collections information first",
            file=sys.stderr,
        )

    # Check the image exists in the images index
    images_resp = api_es_client(pipeline_date).search(
        index=images_index,
        body={
            "query": {"term": {"query.source.sourceIdentifier.value": miro_id}},
        },
    )

    image = None
    if images_resp["hits"]["total"]["value"] == 0:
        print(
            f"✗ Could not find an image for {miro_id} in {images_index}\n"
            "  It could be that the source identifier for this image is that of a Sierra work - that should be suppressed by collections information first",
            file=sys.stderr,
        )
    else:
        image = images_resp["hits"]["hits"][0]
        print(f"✓ Image for {miro_id} found in {images_index}: {image['_id']}")

    return work, image


def _check_dlcs_server(miro_id):
    resp = dlcs_api_client().get(
        f"https://api.dlcs.io/customers/2/spaces/8/images/{miro_id}"
    )

    if resp.status_code == 404:
        print(f"✗ Image {miro_id} not found on DLCS server", file=sys.stderr)
    elif resp.status_code != 200:
        print(f"✗ Error checking DLCS server for {miro_id}: {resp.status_code}", file=sys.stderr)
    else:
        print(f"✓ Image {miro_id} found on DLCS server")


def _set_overrides(*, miro_id, message: str, override_key: str, override_value: str):
    item = _get_vhs_sourcedata_miro_ddb_item(miro_id)
    
    if not item:
        return False

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

    try:
        DYNAMO_CLIENT.update_item(
            TableName=MIRO_TABLE_NAME,
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
        print(f"✓ Successfully set {override_key} override for {miro_id}: {old_value!r} → {override_value!r}")
        _request_reindex_for(miro_id)
        return True
    except Exception as e:
        print(f"✗ Failed to set {override_key} override for {miro_id}: {e}", file=sys.stderr)
        return False


def _remove_override(*, miro_id, message: str, override_key: str):
    item = _get_vhs_sourcedata_miro_ddb_item(miro_id)
    
    if not item:
        return False

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

    try:
        DYNAMO_CLIENT.update_item(
            TableName=MIRO_TABLE_NAME,
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
        print(f"✓ Successfully removed {override_key} override for {miro_id} (was: {old_value!r})")
        _request_reindex_for(miro_id)
        return True
    except Exception as e:
        print(f"✗ Failed to remove {override_key} override for {miro_id}: {e}", file=sys.stderr)
        return False


# github 

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


# DDB sourcedata_miro and reindex

def _set_image_availability(*, miro_id, message: str, is_available: bool):
    item = _get_vhs_sourcedata_miro_ddb_item(miro_id)
    
    if not item:
        return False
    
    
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

    try:
        DYNAMO_CLIENT.update_item(
            TableName=MIRO_TABLE_NAME,
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

        availability_status = "available" if is_available else "suppressed"
        print(f"✓ Successfully set image availability for {miro_id}: {availability_status}")
        _request_reindex_for(miro_id)
        return True
    except Exception as e:
        print(f"✗ Failed to set image availability for {miro_id}: {e}", file=sys.stderr)
        return False


#elasticsearch

def _remove_image_from_elasticsearch(*, miro_id):
    pipeline_date, works_index, images_index = _get_current_pipeline_and_indices()
    work, image = _get_work_and_image(miro_id)

    if work is None or image is None:
        return False

    # Mark the work as deleted
    work["_source"]["debug"]["deletedReason"] = {
        "info": "Miro: isClearedForCatalogueAPI = false",
        "type": "SuppressedFromSource",
    }
    work["_source"]["type"] = "Deleted"

    try:
        index_resp = work_ingestor_es_client(date=pipeline_date).index(
            index=works_index, body=work["_source"], id=work["_id"]
        )
        assert index_resp["result"] == "updated", index_resp
        print(f"✓ Marked work {work['_id']} as deleted in Elasticsearch")
    except Exception as e:
        print(f"✗ Failed to mark work {work['_id']} as deleted: {e}", file=sys.stderr)
        return False

    # Delete the image
    try:
        delete_resp = image_ingestor_es_client(date=pipeline_date).delete(
            index=images_index, id=image["_id"]
        )
        assert delete_resp["result"] == "deleted", delete_resp
        print(f"✓ Deleted image {image['_id']} from Elasticsearch")
        return True
    except Exception as e:
        print(f"✗ Failed to delete image {image['_id']}: {e}", file=sys.stderr)
        return False


# cloudfront

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
        print(f"✓ Successfully invalidated {miro_id} from CloudFront")
        return True
    except ClientError:
        print(f"✗ Failed to invalidate {miro_id} from CloudFront", file=sys.stderr)
        return False


# DLCS 

def _remove_image_from_dlcs(*, miro_id):
    # Wellcome = customer 2, Miro = space 8
    # See https://wellcome.slack.com/archives/CBT40CMKQ/p1621496639019200?thread_ts=1621495275.018100&cid=CBT40CMKQ
    try:
        resp = dlcs_api_client().delete(
            f"https://api.dlcs.io/customers/2/spaces/8/images/{miro_id}"
        )
        if resp.status_code == 404:
            print(f"⚠ Image {miro_id} not found on DLCS server (may already be deleted)", file=sys.stderr)
            return True  # Not found is OK for deletion
        elif resp.status_code == 204:
            print(f"✓ Successfully deleted {miro_id} from DLCS")
            return True
        else:
            print(f"✗ Unexpected response from DLCS when deleting {miro_id}: {resp.status_code}", file=sys.stderr)
            return False
    except Exception as e:
        print(f"✗ Failed to delete {miro_id} from DLCS: {e}", file=sys.stderr)
        return False


def _register_image_on_dlcs(origin_url, miro_id):
    try:
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
        dlcs_response.raise_for_status()
        print(f"✓ Successfully queued {miro_id} for registration on DLCS")
        print(f"  Origin URL: {origin_url}")
        # DLCS will process the above request asynchronously and it may take considerable time.
        # This is particularly true if it is already busy with something else.
        # The response contains details that will allow you to interrogate DLCS to
        # find out whether it has processed (or failed to process - e.g. there's a typo in your origin_url) your request.
        print(f"  DLCS Response: {dlcs_response.text}")
        return True
    except Exception as e:
        print(f"✗ Failed to register {miro_id} on DLCS: {e}", file=sys.stderr)
        return False


# pre-suppression checks

def run_pre_suppression_checks(miro_id):
    _get_vhs_sourcedata_miro_ddb_item(miro_id)
    work, _ = _get_work_and_image(miro_id)
    _check_mets_adapter_store(work)
    _check_dlcs_server(miro_id)


# actual suppression/unsuppression/license update functions

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
    # First, make the DDS record reflect that the image should be visible, and request reindex
    _set_image_availability(miro_id=miro_id, message=message, is_available=True)

    # Now the actual image must be registered on DLCS so that it can be seen
    _register_image_on_dlcs(origin_url=origin, miro_id=miro_id)


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