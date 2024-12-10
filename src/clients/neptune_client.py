import boto3


class NeptuneClient:
    def __init__(self, neptune_endpoint: str):
        endpoint_url = f"https://{neptune_endpoint}:8182"
        self.client = boto3.client("neptunedata", endpoint_url=endpoint_url)

    def run_open_cypher_query(self, query: str):
        """Run a Cypher query against the Neptune cluster"""
        response = self.client.execute_open_cypher_query(openCypherQuery=query)
        return response["results"]

    def get_graph_summary(self):
        return self.client.get_propertygraph_summary(mode="detailed")["payload"]
