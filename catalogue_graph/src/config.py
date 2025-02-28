import os

S3_BULK_LOAD_BUCKET_NAME = os.environ.get("S3_BULK_LOAD_BUCKET_NAME")
GRAPH_QUERIES_SNS_TOPIC_ARN = os.environ.get("GRAPH_QUERIES_SNS_TOPIC_ARN")

LOC_SUBJECT_HEADINGS_URL = (
    "https://id.loc.gov/download/authorities/subjects.madsrdf.jsonld.gz"
)
LOC_NAMES_URL = "https://id.loc.gov/download/authorities/names.madsrdf.jsonld.gz"
MESH_URL = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.gz"
WIKIDATA_SPARQL_URL = "https://query.wikidata.org/sparql"
CATALOGUE_SNAPSHOT_URL = (
    "https://data.wellcomecollection.org/catalogue/v2/works.json.gz"
)

# Ingestor configuration
INGESTOR_S3_BUCKET = os.environ.get("INGESTOR_S3_BUCKET")
INGESTOR_S3_PREFIX = os.environ.get("INGESTOR_S3_PREFIX")
INGESTOR_SHARD_SIZE = os.environ.get("INGESTOR_SHARD_SIZE")
INGESTOR_ES_INDEX = os.environ.get("INGESTOR_ES_INDEX")
INGESTOR_ES_HOST = os.environ.get("INGESTOR_ES_HOST")
INGESTOR_ES_PORT = os.environ.get("INGESTOR_ES_PORT")
INGESTOR_ES_SCHEME = os.environ.get("INGESTOR_ES_SCHEME")
INGESTOR_ES_API_KEY = os.environ.get("INGESTOR_ES_API_KEY")
INGESTOR_PIPELINE_DATE = os.environ.get("INGESTOR_PIPELINE_DATE")
