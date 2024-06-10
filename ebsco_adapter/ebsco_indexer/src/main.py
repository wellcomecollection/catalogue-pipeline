import argparse
import os
import json

import boto3
import pymarc
import elasticsearch

from local_utils import construct_sns_event

ES_INDEX_NAME = os.environ.get("ES_INDEX")


def load_s3_file_streaming_body(s3_bucket, s3_key):
    s3 = boto3.Session().client("s3")
    response = s3.get_object(Bucket=s3_bucket, Key=s3_key)
    return response["Body"]


def get_elasticsearch_client():
    secretsmanager = boto3.Session().client("secretsmanager")

    def _get_secretsmanager_value(secret_id: str):
        return secretsmanager.get_secret_value(SecretId=secret_id)["SecretString"]

    api_key = _get_secretsmanager_value("reporting/ebsco_indexer/es_apikey")
    es_host = _get_secretsmanager_value("reporting/es_host")

    return elasticsearch.Elasticsearch(f"https://{es_host}", api_key=api_key)


def construct_elasticsearch_documents(ebsco_record: pymarc.record.Record):
    """Takes a pymarc `Record` object and turns it into dictionaries (documents) for Elasticsearch indexing."""
    documents = {}

    # The unique ID of the EBSCO item is always stored under the 001 tag
    ebsco_item_id = ebsco_record["001"].data

    # The data in each tag is indexed as a separate document
    for i, tag_data in enumerate(ebsco_record):
        tag_attributes = tag_data.__dict__

        tag = tag_attributes["tag"]
        position = i + 1

        document_id = f"{ebsco_item_id}-{tag}-{position}"
        document = {"parent.id": ebsco_item_id, "tag": tag, "position": position}

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


def index_documents(
    elasticsearch_client, documents: dict[str, dict], ebsco_item_id: str
):
    success_count, fail_count = 0, 0
    for document_id, document in documents.items():
        try:
            elasticsearch_client.index(
                index=ES_INDEX_NAME, id=document_id, document=document
            )
            success_count += 1
        except Exception as e:
            print(f"Failed to index document with id {document_id}: {e}")
            fail_count += 1

    if success_count > 0:
        print(
            f"Successfully indexed {success_count} documents with the parent ID {ebsco_item_id}."
        )
    if fail_count > 0:
        raise Exception(f"Failed to index {fail_count} documents with the parent ID {ebsco_item_id}. See above for individual exceptions for each document.")


def delete_documents_by_parent_id(elasticsearch_client, ebsco_item_id: str):
    body = {"query": {"match": {"parent.id": ebsco_item_id}}}
    result = elasticsearch_client.delete_by_query(index=ES_INDEX_NAME, body=body)
    print(f"Deleted {result['deleted']} documents with the parent ID {ebsco_item_id}.")


def extract_sns_message_from_event(event):
    sns_message = json.loads(event["Records"][0]["Sns"]["Message"])
    return sns_message


def lambda_handler(event, context):
    print(f"Starting lambda_handler, got event: {event}")

    sns_message = extract_sns_message_from_event(event)
    is_deleted = sns_message["deleted"]
    ebsco_item_id = sns_message["id"]

    elasticsearch_client = get_elasticsearch_client()

    # If the item is flagged as deleted, remove it from the Elasticsearch index.
    # Otherwise, extract the item from S3 and index it.
    if is_deleted:
        delete_documents_by_parent_id(elasticsearch_client, ebsco_item_id)
    else:
        s3_bucket = sns_message["location"]["bucket"]
        s3_key = sns_message["location"]["key"]

        ebsco_item_xml = load_s3_file_streaming_body(s3_bucket, s3_key)
        ebsco_item = pymarc.marcxml.parse_xml_to_array(ebsco_item_xml)[0]
        documents = construct_elasticsearch_documents(ebsco_item)
        index_documents(elasticsearch_client, documents, ebsco_item_id)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Index EBSCO item fields into the Elasticsearch reporting cluster."
    )
    parser.add_argument(
        "--ebsco-id", type=str, help="ID of the EBSCO item to index", required=True
    )
    parser.add_argument(
        "--s3-bucket",
        type=str,
        help="S3 bucket storing the raw EBSCO XML file.",
        required=True,
    )
    parser.add_argument(
        "--s3-key",
        type=str,
        help="S3 key storing the raw EBSCO XML file.",
        required=True,
    )
    parser.add_argument(
        "--delete",
        type=bool,
        help="Set to true to remove the item from the index",
        default=False,
    )
    args = parser.parse_args()

    event = construct_sns_event(**vars(args))
    lambda_handler(event, None)
