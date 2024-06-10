import json

import boto3
from elasticsearch import Elasticsearch

EBSCO_FIELDS_INDEX_NAME = "ebsco_fields"
API_KEY = "<INSERT ELASTICSEARCH API KEY HERE>"


def _get_elasticsearch_host():
    secretsmanager = boto3.Session(profile_name="platform-developer").client(
        "secretsmanager"
    )
    return secretsmanager.get_secret_value(SecretId="reporting/es_host")["SecretString"]


def recreate_elasticsearch_index(
    index_name,
    field_mappings: dict,
    api_key: str = None,
    username: str = None,
    password: str = None,
):
    """
    Removes the existing index (and all documents it indexes) and recreates it again with the specified field_mapping
    """
    if api_key is None and (username is None or password is None):
        raise ValueError(
            "Please provide either an `api_key` or a `username` and `password` to authenticate with Elasticsearch."
        )

    host_url = f"https://{_get_elasticsearch_host()}"
    if api_key is not None:
        elasticsearch = Elasticsearch(host_url, api_key=api_key)
    else:
        elasticsearch = Elasticsearch(host_url, basic_auth=(username, password))

    elasticsearch.options(ignore_status=[400, 404]).indices.delete(index=index_name)
    elasticsearch.indices.create(index=index_name, mappings=field_mappings)


if __name__ == "__main__":
    # Load mappings from config file
    with open("config/index_mappings.json", "r") as f:
        mappings = json.loads(f.read())

    # Recreate the index with the loaded mappings
    recreate_elasticsearch_index(
        index_name=EBSCO_FIELDS_INDEX_NAME,
        field_mappings=mappings,
        api_key=API_KEY,
    )
