import os
import io


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
    def index(self, index: str, id: str, document: dict):
        pass

    def delete_by_query(self, index: str, body: dict):
        return {"deleted": 56}


class MockBoto3Session:
    def client(self, client_name: str):
        if client_name == "secretsmanager":
            return MockSecretsManagerClient()
        if client_name == "s3":
            return MockS3Client()
