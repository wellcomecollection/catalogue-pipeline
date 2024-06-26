import argparse
import os
import json

import boto3
import pymarc
import elasticsearch

from local_utils import construct_sqs_event

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
    elasticsearch_client: elasticsearch.Elasticsearch, documents: dict[str, dict]
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
        print(f"Successfully indexed {success_count} documents.")
    if fail_count > 0:
        raise Exception(
            f"Failed to index {fail_count} documents. See above for individual exceptions for each document."
        )


def delete_documents_by_parent_id(elasticsearch_client, ebsco_item_ids: list[str]):
    body = {"query": {"terms": {"parent.id": ebsco_item_ids}}}
    result = elasticsearch_client.delete_by_query(index=ES_INDEX_NAME, body=body)
    print(f"Deleted {result['deleted']} documents.")


def extract_sns_messages_from_sqs_event(event):
    sns_messages = []

    for record in event["Records"]:
        sns_message = json.loads(record["body"])["Message"]
        sns_messages.append(json.loads(sns_message))

    return sns_messages


def index_ebsco_items_in_bulk(
    elasticsearch_client: elasticsearch.Elasticsearch, items_to_index: dict[str, dict]
):
    all_documents = {}
    for sns_message in items_to_index.values():
        s3_bucket = sns_message["location"]["bucket"]
        s3_key = sns_message["location"]["key"]

        ebsco_item_xml = load_s3_file_streaming_body(s3_bucket, s3_key)
        ebsco_item = pymarc.marcxml.parse_xml_to_array(ebsco_item_xml)[0]
        ebsco_item_documents = construct_elasticsearch_documents(ebsco_item)
        all_documents |= ebsco_item_documents

    index_documents(elasticsearch_client, all_documents)


def lambda_handler(event, context):
    print(f"Starting lambda_handler, got event: {event}")

    sns_messages = extract_sns_messages_from_sqs_event(event)

    items_to_index, items_to_delete = {}, {}
    for sns_message in sns_messages:
        is_deleted = sns_message["deleted"]
        ebsco_item_id = sns_message["id"]

        if is_deleted:
            items_to_delete[ebsco_item_id] = sns_message
        else:
            items_to_index[ebsco_item_id] = sns_message

    elasticsearch_client = get_elasticsearch_client()

    if len(items_to_delete) > 0:
        delete_documents_by_parent_id(
            elasticsearch_client, list(items_to_delete.keys())
        )

    if len(items_to_index) > 0:
        index_ebsco_items_in_bulk(elasticsearch_client, items_to_index)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Index EBSCO item fields into the Elasticsearch reporting cluster."
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

    event = construct_sqs_event(
        s3_bucket=args.s3_bucket, s3_keys_to_index_or_delete={args.s3_key: args.delete}
    )
    lambda_handler(event, None)
