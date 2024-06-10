import os
import io
from collections import defaultdict

MOCK_API_KEY = "TEST_SECRET_API_KEY_123"


def get_absolute_path(relative_path: str):
    current_dir = os.path.dirname(os.path.realpath(__file__))
    return os.path.join(current_dir, relative_path)


class MockSecretsManagerClient:
    def get_secret_value(self, SecretId: str):
        if SecretId == "reporting/ebsco_indexer/es_apikey":
            secret_value = MOCK_API_KEY
        elif SecretId == "reporting/es_host":
            secret_value = "test_host.com"
        else:
            raise KeyError("Secret value does not exist.")

        return {"SecretString": secret_value}


class MockS3Client:
    def get_object(self, Bucket: str, Key: str):
        if f"{Bucket}/{Key}" == "test_bucket/prod/test_id_1":
            fixture_name = "fixtures/ebsco_item_fixture_1.xml"
        elif f"{Bucket}/{Key}" == "test_bucket/prod/test_id_2":
            fixture_name = "fixtures/ebsco_item_fixture_2.xml"
        else:
            raise FileNotFoundError("There is no fixture corresponding to this Bucket/Key combination.")

        with open(get_absolute_path(fixture_name), "rb") as f:
            body = f.read()

        return {"Body": io.BytesIO(body)}


class MockElasticsearchClient:
    indexed_documents = defaultdict(dict[str, dict])

    def __init__(self, host: str, api_key: str):
        if api_key != MOCK_API_KEY:
            raise ValueError("Incorrect api_key value.")

    def index(self, index: str, id: str, document: dict):
        self.indexed_documents[index][id] = document

    def delete_by_query(self, index: str, body: dict):
        deleted_parent_id = body["query"]["match"]["parent.id"]

        new_indexed_documents = {}

        deleted_count = 0
        for _id, document in self.indexed_documents[index].items():
            if document["parent.id"] != deleted_parent_id:
                new_indexed_documents[_id] = document
            else:
                deleted_count += 1

        self.indexed_documents[index] = new_indexed_documents

        return {"deleted": deleted_count}


class MockBoto3Session:
    def __init__(self):
        self.clients = {
            "secretsmanager": MockSecretsManagerClient(),
            "s3": MockS3Client(),
        }

    def client(self, client_name: str):
        if client_name not in self.clients:
            raise KeyError("There is no mock client for the specified client_name.")

        return self.clients[client_name]
