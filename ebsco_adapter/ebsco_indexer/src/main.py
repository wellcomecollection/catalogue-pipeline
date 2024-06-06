import os
import json
from datetime import datetime

import boto3
import pymarc
from elasticsearch import Elasticsearch, ApiError

session = boto3.Session()
secretsmanager = session.client("secretsmanager")
s3 = session.client("s3")

ES_INDEX_NAME = os.environ.get("ES_INDEX")


def load_s3_file_streaming_body(s3_bucket, s3_key) -> boto3.response.StreamingBody:
    response = s3.get_object(Bucket=s3_bucket, Key=s3_key)
    return response["Body"]


def get_elasticsearch_client():
    def _get_secretsmanager_value(secret_id: str):
        return secretsmanager.get_secret_value(SecretId=secret_id)["SecretString"]

    api_key = _get_secretsmanager_value("reporting/ebsco_indexer/es_apikey")
    es_host = _get_secretsmanager_value("reporting/es_host")
    return Elasticsearch(es_host, api_key=api_key)


def construct_elasticsearch_documents(ebsco_record: pymarc.record.Record):
    """Takes a pymarc `Record` object and turns it into dictionaries (documents) for Elasticsearch indexing."""
    documents = {}

    # The unique ID of the EBSCO item is always stored under the 001 tag
    ebsco_item_id = ebsco_record["001"].data

    # The data in each tag is indexed as a separate document
    for i, tag_data in enumerate(ebsco_record):
        tag_attributes = tag_data.__dict__

        tag = tag_attributes["tag"]
        position = i+1

        document_id = f"{ebsco_item_id}-{tag}-{position}"
        document = {
            "parent.id": ebsco_item_id,
            "tag": tag,
            "position": position
        }

        if "data" in tag_attributes:
            document["data"] = tag_attributes["data"]
        if "subfields" in tag_attributes:
            document["subfields.code"] = tag_attributes["subfields"][0::2]
            document["subfields.content"] = tag_attributes["subfields"][1::2]
        if "indicators" in tag_attributes:
            document["ind1"] = tag_attributes["indicators"][0]
            document["ind2"] = tag_attributes["indicators"][1]

        documents[document_id] = document

    return documents


def index_documents(elasticsearch_client, documents: dict[str, dict]):
    for document_id, document in documents.items():
        try:
            elasticsearch_client.index(index=ES_INDEX_NAME, id=document_id, document=document)
        except ApiError as e:
            print(f"Failed to index document with id {document_id}: {e}")


# This is required to ensure that the datetime is in the correct format
# for the update_notifier function, Python's datetime.isoformat() does not
# include the 'Z' at the end of the string for older versions of Python.
def _get_iso8601_invoked_at():
    invoked_at = datetime.utcnow().isoformat()
    if invoked_at[-1] != "Z":
        invoked_at += "Z"
    return invoked_at


def lambda_handler(event, context):
    invoked_at = _get_iso8601_invoked_at()
    if "invoked_at" in event:
        invoked_at = event["invoked_at"]

    print(f"Starting lambda_handler @ {invoked_at}, got event: {event}")

    ebsco_item_xml = load_s3_file_streaming_body("wellcomecollection-platform-ebsco-adapter", "prod/xml/2024-05-23/ebs100013422e.xml")
    ebsco_item = pymarc.marcxml.parse_xml_to_array(ebsco_item_xml)[0]

    elasticsearch_client = get_elasticsearch_client()

    documents = construct_elasticsearch_documents(ebsco_item)
    index_documents(elasticsearch_client, documents)


if __name__ == "__main__":
    event = {}

    lambda_handler(event, None)
