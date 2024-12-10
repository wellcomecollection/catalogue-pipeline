import json

import requests
import boto3
from botocore.auth import SigV4Auth
from botocore.awsrequest import AWSRequest

import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


class NeptuneClient:
    def __init__(self, load_balancer_url: str, neptune_endpoint: str):
        self.load_balancer_url = load_balancer_url
        self.neptune_endpoint = neptune_endpoint
        self.session = boto3.Session(profile_name="platform-developer")

    def _make_request(self, method: str, relative_url: str, payload: dict):
        url = f"{self.load_balancer_url}{relative_url}"
        headers = {
            "Host": self.neptune_endpoint,
            "Content-Type": "application/json"
        }
        data = json.dumps(payload)

        # We use IAM database authentication, which means we need to authenticate the request using AWS Signature
        request = AWSRequest(method=method, url=url, data=data, headers=headers)
        SigV4Auth(self.session.get_credentials(), "neptune-db", "eu-west-1").add_auth(request)

        # We need to send a manual request rather than using boto3 since we are accessing the instance via a NLB
        # We are using the default NLB DNS name, which does not support custom SSL certificates, so we need to 
        # disable SSL certificate verification. This increases the risks of a man-in-the-middle attack, 
        # which is acceptable for a testing database. In production, we will be connecting to the database 
        # directly from within the VPC.
        response = requests.request(method, url, data=data, headers=request.headers, verify=False)

        if response.status_code != 200:
            raise Exception(response.content)

        return response.json()

    def run_open_cypher_query(self, query: str):
        """Run a Cypher query against an experimental serverless Neptune cluster"""
        payload = {"query": query}
        response = self._make_request("POST", "/openCypher", payload)
        return response['results']

    def get_graph_summary(self):
        response = self._make_request("GET", "/propertygraph/statistics/summary", {})
        return response["payload"]['graphSummary']
