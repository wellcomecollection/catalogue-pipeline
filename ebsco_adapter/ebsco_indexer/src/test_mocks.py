import os
import io
from collections import defaultdict


def get_absolute_path(relative_path: str):
    current_dir = os.path.dirname(os.path.realpath(__file__))
    return os.path.join(current_dir, relative_path)


class MockSecretsManagerClient:
    def get_secret_value(self, SecretId: str):
        return {"SecretString": "TEST"}


class MockS3Client:
    def get_object(self, Bucket: str, Key: str):
        with open(get_absolute_path("fixtures/ebsco_item_fixture.xml"), "rb") as f:
            body = f.read()

        return {"Body": io.BytesIO(body)}


class MockElasticsearchClient:
    indexed_documents = defaultdict(dict)

    def __init__(self, host: str, api_key: str):
        pass

    def index(self, index: str, id: str, document: dict):
        print(document)
        self.indexed_documents[index][id] = document

    def delete_by_query(self, index: str, body: dict):
        print(self.indexed_documents)
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
        return self.clients[client_name]
