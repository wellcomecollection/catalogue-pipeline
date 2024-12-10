import itertools
import json
from collections.abc import Generator
import enum
from typing import Literal

import boto3

from extractors.loc.concepts_extractor import LibraryOfCongressConceptsExtractor
import query_builders.cypher as cypher


CHUNK_SIZE = 100
LOC_SH_URL = "https://id.loc.gov/download/authorities/subjects.skosrdf.jsonld.gz"


def publish_to_sns(query: str):
    client = boto3.client("sns")
    client.publish(
        TopicArn="arn:aws:sns:eu-west-1:760097843905:catalogue_graph_queries",
        Message=json.dumps({"default": query}),
        MessageStructure="json",
    )


def _generator_to_chunks(items: Generator):
    while True:
        chunk = list(itertools.islice(items, CHUNK_SIZE))
        if chunk:
            yield chunk
        else:
            return


class GraphExtractorType(enum.Enum):
    LOC_SH = LibraryOfCongressConceptsExtractor(LOC_SH_URL)
    LOC_LOCATION = LibraryOfCongressConceptsExtractor(LOC_SH_URL)


def extract_all(
    extractor_type: GraphExtractorType, entity_type: Literal["nodes", "edges"]
):
    extractor = GraphExtractorType[extractor_type.name].value

    if entity_type == "nodes":
        entities = extractor.extract_nodes()
    elif entity_type == "edges":
        entities = extractor.extract_edges()
    else:
        raise ValueError("Unsupported entity type.")

    for chunk in _generator_to_chunks(entities):
        query = cypher.construct_upsert_nodes_query(chunk)
        publish_to_sns(query)


def lambda_handler(event: dict, context):
    extract_all(GraphExtractorType.LOC_SH, "edges")


if __name__ == "__main__":
    lambda_handler({}, None)
