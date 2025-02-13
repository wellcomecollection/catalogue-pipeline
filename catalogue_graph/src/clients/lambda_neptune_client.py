import boto3

from .base_neptune_client import BaseNeptuneClient


class LambdaNeptuneClient(BaseNeptuneClient):
    def __init__(self, neptune_endpoint: str):
        super().__init__(neptune_endpoint)

        self.session = boto3.Session()

    def _get_client_url(self) -> str:
        return f"https://{self.neptune_endpoint}:8182"
