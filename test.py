import boto3

from extractors.loc_concepts_extractor import LibraryOfCongressConceptsExtractor
from clients.neptune_client import NeptuneClient
from clients.cypher_client import CypherClient

import itertools


def _get_secret(secret_name: str):
    session = boto3.Session(profile_name="platform-developer")
    secrets_manager_client = session.client('secretsmanager', region_name='eu-west-1')
    response = secrets_manager_client.get_secret_value(SecretId=secret_name)

    return response['SecretString']


url = "https://id.loc.gov/download/authorities/subjects.skosrdf.jsonld.gz"
loc_extractor = LibraryOfCongressConceptsExtractor(url)

neptune_client = NeptuneClient(_get_secret("NeptuneTest/LoadBalancerUrl"), _get_secret("NeptuneTest/InstanceEndpoint"))
cypher_client = CypherClient(neptune_client)

sample_nodes = loc_extractor.extract_sample_nodes(3000)
sample_edges = loc_extractor.extract_sample_edges(300)

while True:
    chunk = list(itertools.islice(sample_edges, 1))
    if chunk:
        print(cypher_client.upsert_edges(chunk))
        #print(cypher_client.create_source_concept_nodes(chunk))
    else:
        break

