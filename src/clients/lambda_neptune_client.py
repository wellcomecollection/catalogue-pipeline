import boto3
import os

from .base_neptune_client import BaseNeptuneClient


class LambdaNeptuneClient(BaseNeptuneClient):
    def __init__(self, neptune_endpoint: str):
        super().__init__()
        self.neptune_endpoint = neptune_endpoint
        self.session = boto3.Session(
            aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID"),
            aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY"),
            aws_session_token=os.getenv("AWS_SESSION_TOKEN"),
        )

    def _get_client_url(self):
        return f"https://{self.neptune_endpoint}:8182"
