#!/usr/bin/env python3
# -*- encoding: utf-8
"""
This script can "spike" an image on wellcomecollection.org/works -- remove
it from the site and prevent it from reappearing in a reindex.
"""

import datetime as dt
import getpass
import hashlib
import json
import os

import boto3
import click
import requests


def sha256(bs):
    h = hashlib.sha256()
    h.update(bs)
    return h.hexdigest()


def aws_client(service_name, role_arn):
    sts = boto3.client("sts")
    session_name = "%s--%s" % (getpass.getuser(), os.path.basename(__file__))
    resp = sts.assume_role(RoleArn=role_arn, RoleSessionName=session_name)

    credentials = resp["Credentials"]

    return boto3.client(
        service_name,
        aws_access_key_id=credentials["AccessKeyId"],
        aws_secret_access_key=credentials["SecretAccessKey"],
        aws_session_token=credentials["SessionToken"],
        region_name="eu-west-1",
    )


def platform_client(service_name):
    return aws_client(service_name, "arn:aws:iam::760097843905:role/platform-developer")


def catalogue_client(service_name):
    return aws_client(
        service_name, "arn:aws:iam::756629837203:role/catalogue-developer"
    )


def get_associated_image_remover(es_host, es_auth, catalogue_id, works_indices):
    print("*** Trying to find associated images")
    images_indices = [idx.replace("works", "images") for idx in works_indices]

    all_associated_images = {}
    for index_name in images_indices:
        print(f"··· Searching for {catalogue_id} in index {index_name}")
        request_body = {
            "query": {
                "multi_match": {
                    "query": catalogue_id,
                    "fields": [
                        "source.canonicalWork.id.canonicalId",
                        "source.redirectedWork.id.canonicalId"
                    ]
                }
            },
            "_source": False
        }
        resp = requests.get(
            f"{es_host}{index_name}/_doc/{catalogue_id}", auth=es_auth, data=request_body
        )

        if resp.status_code == 403 or resp.status_code == 404:
            print(f"··· Index {index_name} does not exist")
            print("··· (This is likely due to index names not being of the form works-*, images-*")
            continue

        data = resp.json()
        associated_image_ids = [hit["_id"] for hit in data["hits"]["hits"]]
        if associated_image_ids:
            print(f"··· Found {len(associated_image_ids)} associated images in {index_name}")
        else:
            print(f"··· Did not find any associated images in {index_name}")

        all_associated_images[index_name] = associated_image_ids

    def remove_associated_images(dry_run):
        deletions = []
        for index, ids in all_associated_images.items():
            for id in ids:
                if click.confirm(f"Remove associated image {id} from {index}?"):
                    deletions.append((index, id))

        if not dry_run:
            for index, id in deletions:
                resp = requests.delete(f"{es_host}{index}/_doc/{id}", auth=es_auth)
                assert resp.status_code == 200, resp.json()
                print(f"··· Deleted {index}/{id}")
        else:
            print("Dry run, deletions are:")
            for index, id in deletions:
                print(f"- {index}/{id}")

    return remove_associated_images



def remove_image_from_es_indexes(catalogue_id, indices, dry_run):
    print("*** Removing the image from our Elasticsearch indexes")

    ecs_client = catalogue_client("ecs")
    ssm_client = catalogue_client("ssm")

    # AWLC: Yes, it would be more robust if we got this config by checking the
    # ingestor task definition to see what the current parameters are, but that's
    # such a faff with the namespace that I literally CBA.
    print("··· Reading Elasticsearch config for the ingestor (write credentials)")
    es_username = ssm_client.get_parameter(
        Name="/aws/reference/secretsmanager/catalogue/takedown/es_username",
        WithDecryption=True,
    )
    es_password = ssm_client.get_parameter(
        Name="/aws/reference/secretsmanager/catalogue/takedown/es_password",
        WithDecryption=True,
    )
    es_auth = (es_username["Parameter"]["Value"], es_password["Parameter"]["Value"])

    print("··· Getting the task definitions for the catalogue API")
    resp = ecs_client.list_services(cluster="catalogue-api")
    service_arns = resp["serviceArns"]

    resp = ecs_client.describe_services(cluster="catalogue-api", services=service_arns)
    services = resp["services"]
    task_definitions = [service["taskDefinition"] for service in services]

    miro_id = None
    remove_associated_images = None

    print("··· Reading Elastic Cloud config for the catalogue API (read credentials)")
    for td in task_definitions:
        resp = ecs_client.describe_task_definition(taskDefinition=td)
        container_definitions = resp["taskDefinition"]["containerDefinitions"]
        app_containers = [cd for cd in container_definitions if cd["name"] == "app"]
        assert len(app_containers) == 1

        app_secrets = {s["name"]: s["valueFrom"] for s in app_containers[0]["secrets"]}

        # This TD is not for the API
        if "es_host" not in app_secrets:
            continue

        for name, value_from in app_secrets.items():
            resp = ssm_client.get_parameter(Name=value_from, WithDecryption=True)
            app_secrets[name] = resp["Parameter"]["Value"]

        # 3. Once we have the config and password, we can remove the work from
        # the Elasticsearch index (if present).
        es_host = "%s://%s:%s/" % (
            app_secrets["es_protocol"],
            app_secrets["es_host"],
            app_secrets["es_port"],
        )

        for index_name in indices:
            print("··· Looking up %s in index %s" % (catalogue_id, index_name))
            resp = requests.get(
                f"{es_host}{index_name}/_doc/{catalogue_id}", auth=es_auth
            )

            if resp.status_code == 404:
                print("··· Work is not in this index, skipping")
                continue

            assert resp.status_code == 200, resp.json()

            existing_work = resp.json()["_source"]

            if existing_work["type"] == "Invisible":
                print(
                    "··· Work is already suppressed as Invisible, skipping"
                )
                continue

            # While we're looking at API responses, try to get the Miro ID.
            identifiers = [existing_work["state"]["sourceIdentifier"]] + existing_work["data"][
                "otherIdentifiers"
            ]
            miro_identifiers = [
                idf
                for idf in identifiers
                if idf["identifierType"]["id"] == "miro-image-number"
            ]
            assert len(miro_identifiers) == 1
            miro_identifier = miro_identifiers[0]["value"]
            if miro_id is None:
                miro_id = miro_identifier
            else:
                assert miro_id == miro_identifier, "Multiple Miro IDs? %s, %s" % (
                    miro_id,
                    miro_identifier,
                )

            assert existing_work["type"] == "Visible"

            # It's necessary to fill in the data field so that Circe can
            # decode Invisible works
            blank_data = {}
            for key, value in existing_work["data"].items():
                if isinstance(value, list):
                    blank_data[key] = []
                elif isinstance(value, bool):
                    blank_data[key] = False
                else:
                    blank_data[key] = None

            new_work = {
                "type": "Invisible",
                "data": blank_data,
                "version": existing_work["version"],
                "state": {
                    **existing_work["state"],
                    "modifiedTime": dt.datetime.now().isoformat()
                },
                "canonicalId": existing_work["canonicalId"],
                "sourceIdentifier": existing_work["sourceIdentifier"],
            }

            print("··· Replacing work with an Invisible work")
            if not dry_run:
                resp = requests.put(
                    f"{es_host}{index_name}/_doc/{catalogue_id}",
                    auth=es_auth,
                    json=new_work,
                )
                resp.raise_for_status()

                print("··· Asserting work was made invisible")
                resp = requests.get(
                    f"{es_host}{index_name}/_doc/{catalogue_id}", auth=es_auth
                )
                assert resp.json()["_source"]["type"] == "Invisible"
            else:
                print("Dry run, new work is:")
                print(json.dumps(new_work))

        remove_associated_images = get_associated_image_remover(es_host, es_auth, catalogue_id, indices)

    return miro_id, remove_associated_images


def suppress_work_in_miro_vhs(miro_id, dry_run):
    print("*** Marking the image as withdrawn in the Miro VHS")
    dynamodb_client = platform_client("dynamodb")

    resp = dynamodb_client.get_item(
        TableName="vhs-sourcedata-miro", Key={"id": {"S": miro_id}}
    )
    item = resp["Item"]

    if not item["isClearedForCatalogueAPI"]["BOOL"]:
        print("··· Work is already withdrawn in VHS")
        return

    item["isClearedForCatalogueAPI"]["BOOL"] = False
    item["version"]["N"] = str(int(item["version"]["N"]) + 1)

    # AWLC: I should do a conditional PutItem here because it's a VHS, but I CBA.
    # This table isn't usually changing much.
    if not dry_run:
        resp = dynamodb_client.put_item(TableName="vhs-sourcedata-miro", Item=item)

        resp = dynamodb_client.get_item(
            TableName="vhs-sourcedata-miro", Key={"id": {"S": miro_id}}
        )
        assert not resp["Item"]["isClearedForCatalogueAPI"]["BOOL"]
    else:
        print("Dry run, new VHS entry is:")
        print(json.dumps(item));


def remove_image_from_loris_s3_bucket(miro_id, dry_run):
    print("*** Removing the image from the Loris S3 bucket")
    s3_client = platform_client("s3")

    shard = miro_id[:-3] + "000"
    prefix = f"{shard}/{miro_id}"
    bucket = "wellcomecollection-miro-images-public"
    print("··· Looking up objects under prefix s3://%s/%s" % (bucket, prefix))

    resp = s3_client.list_objects_v2(Bucket=bucket, Prefix=prefix)
    matching_keys = [obj["Key"] for obj in resp.get("Contents", [])]

    if not matching_keys:
        print("··· No matching objects in S3 bucket, skipping")
        return

    assert len(matching_keys) == 1, matching_keys
    key = matching_keys[0]
    assert key.endswith(".jpg")

    print("··· Detected object in S3 bucket, deleting: %s" % key)
    if not dry_run:
        s3_client.delete_object(Bucket=bucket, Key=key)


def create_cloudfront_invalidations(miro_id, dry_run):
    print("*** Creating a CloudFront invalidation for Loris")
    cloudfront_client = platform_client("cloudfront")

    resp = cloudfront_client.list_distributions()
    assert not resp["DistributionList"]["IsTruncated"]
    matching = [
        item
        for item in resp["DistributionList"]["Items"]
        if item["Origins"]["Items"][0]["DomainName"]
        == "iiif-origin.wellcomecollection.org"
    ]
    assert len(matching) == 1
    distribution_id = matching[0]["Id"]
    print("··· Detected Loris CloudFront distribution as %s" % distribution_id)

    url = "/image/%s.jpg/*" % miro_id
    print("··· Issuing an invalidation for %s" % url)

    if not dry_run:
        resp = cloudfront_client.create_invalidation(
            DistributionId=distribution_id,
            InvalidationBatch={
                "Paths": {"Quantity": 1, "Items": [url]},
                "CallerReference": dt.datetime.now().isoformat(),
            },
        )
        assert resp["ResponseMetadata"]["HTTPStatusCode"] == 201


def update_miro_inventory(miro_id, dry_run):
    print("*** Updating the Miro inventory")
    dynamodb_client = platform_client("dynamodb")
    s3_client = platform_client("s3")

    resp = dynamodb_client.get_item(
        TableName="vhs-miro-migration", Key={"id": {"S": miro_id}}
    )
    item = resp["Item"]

    s3_bucket = item["location"]["M"]["namespace"]["S"]
    s3_key = item["location"]["M"]["key"]["S"]
    print("··· Detected VHS inventory entry as s3://%s/%s" % (s3_bucket, s3_key))

    inventory_obj = s3_client.get_object(Bucket=s3_bucket, Key=s3_key)
    inventory_entry = json.load(inventory_obj["Body"])

    inventory_entry["catalogue_api_derivative"] = False
    inventory_entry["catalogue_api_derivative_bucket"] = None
    inventory_entry["catalogue_api_derivative_key"] = None

    new_entry = json.dumps(inventory_entry, separators=(",", ":")).encode("utf8")
    new_key = "%s/%s.json" % (miro_id, sha256(new_entry))
    if new_key == s3_key:
        print("··· Inventory is already up to date, skipping")
        return

    print("··· Updating VHS inventory entry")
    if not dry_run:
        s3_client.put_object(Bucket=s3_bucket, Key=new_key, Body=new_entry)

    item["location"]["M"]["key"]["S"] = new_key
    item["version"]["N"] = str(int(item["version"]["N"]) + 1)

    if not dry_run:
        dynamodb_client.put_item(TableName="vhs-miro-migration", Item=item)
    else:
        print("Dry run, new VHS item is:")
        print(json.dumps(item))


@click.command()
@click.argument("catalogue_id")
@click.option("-i", "--index", multiple=True, required=True)
@click.option("--dry-run", default=False, is_flag=True)
def main(catalogue_id, index, dry_run):
    print("*** Suppressing work ID %s" % catalogue_id)
    miro_id, remove_associated_images = remove_image_from_es_indexes(catalogue_id=catalogue_id, indices=index, dry_run=dry_run)
    assert miro_id is not None, "Don't know the Miro ID!"
    print("*** Detected Miro ID as %s" % miro_id)

    remove_associated_images(dry_run)
    suppress_work_in_miro_vhs(miro_id, dry_run)

    remove_image_from_loris_s3_bucket(miro_id, dry_run)
    create_cloudfront_invalidations(miro_id, dry_run)
    update_miro_inventory(miro_id, dry_run)

    print(
        "*** You also need to (manually) create a CloudFront invalidation for the /works page on wellcomecollection.org"
    )


if __name__ == "__main__":
    main()
