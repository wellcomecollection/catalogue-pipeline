import os

S3_BULK_LOAD_BUCKET_NAME = os.environ.get("S3_BULK_LOAD_BUCKET_NAME")
GRAPH_QUERIES_SNS_TOPIC_ARN = os.environ.get("GRAPH_QUERIES_SNS_TOPIC_ARN")

LOC_SUBJECT_HEADINGS_URL = (
    "https://id.loc.gov/download/authorities/subjects.skosrdf.jsonld.gz"
)
LOC_NAMES_URL = "https://id.loc.gov/download/authorities/names.skosrdf.jsonld.gz"
MESH_URL = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.gz"
WIKIDATA_SPARQL_URL = "https://query.wikidata.org/sparql"
CATALOGUE_SNAPSHOT_URL = "https://data.wellcomecollection.org/catalogue/v2/works.json.gz"
