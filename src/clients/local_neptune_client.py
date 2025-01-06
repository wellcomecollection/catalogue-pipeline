import boto3
import urllib3

from .base_neptune_client import BaseNeptuneClient


class LocalNeptuneClient(BaseNeptuneClient):
    def __init__(self, load_balancer_url: str, neptune_endpoint: str):
        # We are using the default NLB DNS name, which does not support custom SSL certificates, so we need to
        # disable SSL certificate verification. This increases the risks of a man-in-the-middle attack,
        # which is acceptable for a testing database. In production, we will be connecting to the database
        # directly from within the VPC.
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        self.verify_requests = False

        self.load_balancer_url = load_balancer_url
        self.neptune_endpoint = neptune_endpoint
        self.session = boto3.Session()

    def _get_client_url(self) -> str:
        return self.load_balancer_url
