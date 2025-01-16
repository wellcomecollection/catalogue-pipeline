import boto3

from .base_neptune_client import BaseNeptuneClient


class LocalNeptuneClient(BaseNeptuneClient):
    def __init__(self, load_balancer_url: str, neptune_endpoint: str):
        super().__init__(neptune_endpoint)

        self.load_balancer_url = load_balancer_url
        self.session = boto3.Session()

    def _get_client_url(self) -> str:
        return self.load_balancer_url
