import argparse
import json
import enum
from typing import Literal

import boto3

from transformers.loc.concepts_transformer import LibraryOfCongressConceptsTransformer
from transformers.loc.names_transformer import LibraryOfCongressNamesTransformer
from transformers.loc.locations_transformer import LibraryOfCongressLocationsTransformer
from transformers.base_transformer import BaseTransformer
import query_builders.cypher as cypher

from clients.local_neptune_client import LocalNeptuneClient
from clients.neptune_client import NeptuneClient


QUERY_CHUNK_SIZE = 200
LOC_SUBJECT_HEADINGS_URL = (
    "https://id.loc.gov/download/authorities/subjects.skosrdf.jsonld.gz"
)
LOC_NAMES_URL = "https://id.loc.gov/download/authorities/names.skosrdf.jsonld.gz"

GRAPH_QUERIES_SNS_TOPIC_ARN = (
    "arn:aws:sns:eu-west-1:760097843905:catalogue_graph_queries"
)


def _get_secret(secret_name: str):
    secrets_manager_client = boto3.client("secretsmanager", region_name="eu-west-1")
    response = secrets_manager_client.get_secret_value(SecretId=secret_name)

    return response["SecretString"]


def publish_to_sns(queries: list[str]):
    messages = [json.dumps({"default": query}) for query in queries]

    boto3.client("sns").publish_batch(
        TopicArn=GRAPH_QUERIES_SNS_TOPIC_ARN,
        Message=messages,
        MessageStructure="json",
    )


class GraphTransformer(enum.Enum):
    LOC_SUBJECT_HEADINGS = LibraryOfCongressConceptsTransformer(
        LOC_SUBJECT_HEADINGS_URL
    )
    LOC_NAMES = LibraryOfCongressNamesTransformer(LOC_NAMES_URL)
    LOC_LOCATIONS = LibraryOfCongressLocationsTransformer(
        LOC_SUBJECT_HEADINGS_URL, LOC_NAMES_URL
    )

    def __str__(self):
        return self.name.lower()

    @staticmethod
    def argparse(s):
        return GraphTransformer[s.upper()]


def stream_to_sns(
    transformer_type: GraphTransformer,
    entity_type: Literal["nodes", "edges"],
    sample_size: int = None,
):
    """Streams selected entities (nodes or edges) into SNS via the selected Transformer."""
    queries = []

    transformer: BaseTransformer = GraphTransformer[transformer_type.name].value

    for chunk in transformer.stream_chunks(entity_type, QUERY_CHUNK_SIZE, sample_size):
        queries.append(cypher.construct_upsert_nodes_query(chunk))

        # SNS supports a maximum batch size of 10
        if len(queries) >= 10:
            publish_to_sns(queries)
            queries = []

    if len(queries) > 0:
        publish_to_sns(queries)


def stream_to_graph(
    neptune_client: NeptuneClient | LocalNeptuneClient,
    transformer_type: GraphTransformer,
    entity_type: Literal["nodes", "edges"],
    sample_size: int = None,
):
    """Streams selected entities (nodes or edges) directly into Neptune via the selected Transformer.
    Only used when testing the `extractor` locally.
    """
    transformer: BaseTransformer = GraphTransformer[transformer_type.name].value

    queries = 0
    for chunk in transformer.stream_chunks(entity_type, QUERY_CHUNK_SIZE, sample_size):
        query = cypher.construct_upsert_nodes_query(chunk)
        queries += 1

        if queries % 10 == 0:
            print(queries)
        # neptune_client.run_open_cypher_query(query)


def lambda_handler(event: dict, context):
    stream_to_sns(GraphTransformer.LOC_NAMES, "nodes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="")
    parser.add_argument(
        "--transformer-type",
        type=GraphTransformer.argparse,
        choices=list(GraphTransformer),
        help="",
        required=True,
    )
    parser.add_argument(
        "--entity-type",
        type=str,
        choices=["nodes", "edges"],
        help="",
        required=True,
    )
    parser.add_argument(
        "--stream-to",
        type=str,
        choices=["sns", "graph"],
        help="",
        required=True,
    )
    parser.add_argument("--sample-size", type=int, help="")
    args = parser.parse_args()

    if args.stream_to == "graph":
        client = LocalNeptuneClient(
            _get_secret("NeptuneTest/LoadBalancerUrl"),
            _get_secret("NeptuneTest/InstanceEndpoint"),
        )
        stream_to_graph(
            client,
            args.transformer_type,
            args.entity_type,
            args.sample_size,
        )
    else:
        stream_to_sns(args.transformer_type, args.entity_type, args.sample_size)
