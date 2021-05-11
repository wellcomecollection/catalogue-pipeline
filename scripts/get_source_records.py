#!/usr/bin/env python
"""
This script allows you to download all the source records associated
with a particular Work.

Example:

    $ python3 get_source_records.py a2242545

It prints a list of temporary files with the source records, which you
can pass directly to your text editor to open them, e.g.

    $ python3 get_source_records.py a2242545 | xargs mate

"""

import json
import os
import sys
import tempfile

import boto3
from elasticsearch import Elasticsearch
import httpx


CALM_ADAPTER_TABLE = "vhs-calm-adapter"
METS_ADAPTER_TABLE = "mets-adapter-store-delta"
MIRO_ADAPTER_TABLE = "vhs-sourcedata-miro"
SIERRA_ADAPTER_TABLE = "vhs-sierra-sierra-adapter-20200604"


def get_session(*, role_arn):
    """
    Returns a boto3 Session authenticated with the current role ARN.
    """
    sts_client = boto3.client("sts")
    assumed_role_object = sts_client.assume_role(
        RoleArn=role_arn, RoleSessionName="AssumeRoleSession1"
    )
    credentials = assumed_role_object["Credentials"]
    return boto3.Session(
        aws_access_key_id=credentials["AccessKeyId"],
        aws_secret_access_key=credentials["SecretAccessKey"],
        aws_session_token=credentials["SessionToken"],
    )


def get_secret_string(session, *, secret_id):
    """
    Look up the value of a SecretString in Secrets Manager.
    """
    secrets = session.client("secretsmanager")
    return secrets.get_secret_value(SecretId=secret_id)["SecretString"]


def get_api_es_client(session):
    """
    Returns an Elasticsearch client for the catalogue cluster.
    """
    host = get_secret_string(session, secret_id="catalogue/api/es_host")
    port = get_secret_string(session, secret_id="catalogue/api/es_port")
    protocol = get_secret_string(session, secret_id="catalogue/api/es_protocol")
    username = get_secret_string(session, secret_id="catalogue/api/es_username")
    password = get_secret_string(session, secret_id="catalogue/api/es_password")

    return Elasticsearch(f"{protocol}://{username}:{password}@{host}:{port}")


def get_current_works_index():
    resp = httpx.get(
        "https://api.wellcomecollection.org/catalogue/v2/search-templates.json"
    )

    return resp.json()["templates"][0]["index"]


def get_source_identifiers(session, *, work_id):
    """
    Return the identifiers for all the records that made up this work.

    This is *not* the same as the list of identifiers presented in the API.

    """
    es_client = get_api_es_client(session)

    works_index = get_current_works_index()

    es_resp = es_client.get(index=works_index, id=work_id)
    work = es_resp["_source"]

    redirect_sources = [rs["sourceIdentifier"] for rs in work["redirectSources"]]

    return [work["state"]["sourceIdentifier"]] + redirect_sources


def _get_vhs_record(session, *, table_name, id):
    dynamodb = session.resource("dynamodb").meta.client
    s3 = session.client("s3")

    try:
        item = dynamodb.get_item(TableName=table_name, Key={"id": id})["Item"]
    except KeyError:
        raise RuntimeError(f"No item with ID {id} in table {table_name}?")

    try:
        bucket = item["payload"]["bucket"]
        key = item["payload"]["key"]
    except KeyError:
        bucket = item["location"]["bucket"]
        key = item["location"]["key"]

    return s3.get_object(Bucket=bucket, Key=key)["Body"].read()


def _prettify_json(s):
    return json.dumps(json.loads(s), indent=2, sort_keys=True)


def _get_mets_record(session, *, b_number):
    dynamodb = session.resource("dynamodb").meta.client
    s3 = session.client("s3")

    try:
        item = dynamodb.get_item(TableName=METS_ADAPTER_TABLE, Key={"id": b_number})[
            "Item"
        ]
    except KeyError:
        raise RuntimeError(f"No METS item with ID {id} in {METS_ADAPTER_TABLE}")

    payload = item["payload"]

    try:
        location = payload["MetsFileWithImages"]["root"]
        s3_obj = s3.get_object(Bucket=location["bucket"], Key=location["key"])
        return s3_obj["Body"].read().encode("utf8")
    except KeyError:
        return None


def get_source_record(session, *, source_identifier, apply_cleanups):
    identifier_type = source_identifier["identifierType"]["id"]

    if identifier_type == "calm-record-id":
        record = _get_vhs_record(
            session, table_name=CALM_ADAPTER_TABLE, id=source_identifier["value"]
        )

        if apply_cleanups:
            return _prettify_json(record)
        else:
            return record.encode("utf8")
    elif identifier_type == "sierra-system-number":
        # The sourceIdentifier has a b-number like b19489936, but the
        # Sierra VHS keys records without their prefix/check digit, ie 1948993
        b_number = source_identifier["value"][1:8]
        record = _get_vhs_record(session, table_name=SIERRA_ADAPTER_TABLE, id=b_number)

        if apply_cleanups:
            transformable = json.loads(record)

            if transformable.get("maybeBibRecord") is not None:
                transformable["maybeBibRecord"]["data"] = json.loads(
                    transformable["maybeBibRecord"]["data"]
                )

            for item_record in transformable["itemRecords"].values():
                item_record["data"] = json.loads(item_record["data"])

            for holdings_record in transformable.get("holdingsRecords", {}).values():
                holdings_record["data"] = json.loads(holdings_record["data"])

            for order_record in transformable.get("orderRecords", {}).values():
                order_record["data"] = json.loads(order_record["data"])

            return json.dumps(transformable, indent=2, sort_keys=True)
        else:
            return record.encode("utf8")
    elif identifier_type == "miro-image-number":
        record = _get_vhs_record(
            session, table_name=MIRO_ADAPTER_TABLE, id=source_identifier["value"]
        )

        if apply_cleanups:
            return _prettify_json(record)
        else:
            return record.encode("utf8")
    elif identifier_type == "mets":
        return _get_mets_record(session, b_number=source_identifier["value"])
    else:
        raise RuntimeError(f"Unrecognised ID type: {identifier_type}")


if __name__ == "__main__":
    try:
        work_id = sys.argv[1]
    except IndexError:
        sys.exit(f"Usage: {__file__} <WORK_ID>")

    apply_cleanups = "--skip-cleanup" not in sys.argv

    session = get_session(role_arn="arn:aws:iam::760097843905:role/platform-developer")

    source_identifiers = get_source_identifiers(session, work_id=work_id)

    temp_dir = tempfile.mkdtemp(prefix=work_id)

    for id in source_identifiers:
        record = get_source_record(
            session, source_identifier=id, apply_cleanups=apply_cleanups
        )

        if record is None:
            continue

        out_path = os.path.join(
            temp_dir, f"{id['identifierType']['id']}_{id['value']}.json"
        )

        with open(out_path, "w") as outfile:
            outfile.write(record)

        print(out_path)
