import os

from utils.types import Environment

NEPTUNE_CLUSTER_IDENTIFIER_DEFAULT = "catalogue-graph"
NEPTUNE_CLUSTER_IDENTIFIER = os.environ.get(
    "NEPTUNE_CLUSTER_IDENTIFIER", NEPTUNE_CLUSTER_IDENTIFIER_DEFAULT
)

NEPTUNE_PROD_HOST_SECRET_NAME_DEFAULT = "catalogue-graph/neptune-cluster-endpoint"
NEPTUNE_PROD_HOST_SECRET_NAME = os.environ.get(
    "NEPTUNE_PROD_HOST_SECRET_NAME", NEPTUNE_PROD_HOST_SECRET_NAME_DEFAULT
)

NEPTUNE_DEV_HOST_SECRET_NAME_DEFAULT = "catalogue-graph-dev/neptune-cluster-endpoint"
NEPTUNE_DEV_HOST_SECRET_NAME = os.environ.get(
    "NEPTUNE_DEV_HOST_SECRET_NAME", NEPTUNE_DEV_HOST_SECRET_NAME_DEFAULT
)

LOC_SUBJECT_HEADINGS_URL = (
    "https://id.loc.gov/download/authorities/subjects.madsrdf.jsonld.gz"
)
LOC_NAMES_URL = "https://id.loc.gov/download/authorities/names.madsrdf.jsonld.gz"
MESH_URL = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2026.gz"
WIKIDATA_SPARQL_URL = "https://query.wikidata.org/sparql"

SLACK_SECRET_ID = os.environ.get("SLACK_SECRET_ID", "")

CATALOGUE_GRAPH_S3_BUCKET_PROD_DEFAULT = "wellcomecollection-catalogue-graph"
CATALOGUE_GRAPH_S3_BUCKET_DEV_DEFAULT = "wellcomecollection-catalogue-graph-dev"

CATALOGUE_GRAPH_S3_BUCKET_PROD = os.environ.get(
    "CATALOGUE_GRAPH_S3_BUCKET_PROD",
    os.environ.get("CATALOGUE_GRAPH_S3_BUCKET", CATALOGUE_GRAPH_S3_BUCKET_PROD_DEFAULT),
)
CATALOGUE_GRAPH_S3_BUCKET_DEV = os.environ.get(
    "CATALOGUE_GRAPH_S3_BUCKET_DEV", CATALOGUE_GRAPH_S3_BUCKET_DEV_DEFAULT
)


def get_catalogue_graph_s3_bucket(environment: Environment) -> str:
    if environment == "prod":
        return CATALOGUE_GRAPH_S3_BUCKET_PROD

    return CATALOGUE_GRAPH_S3_BUCKET_DEV

INGESTOR_S3_PREFIX_DEFAULT = "ingestor"
INGESTOR_S3_PREFIX = os.environ.get("INGESTOR_S3_PREFIX", INGESTOR_S3_PREFIX_DEFAULT)

BULK_LOADER_S3_PREFIX_DEFAULT = "graph_bulk_loader"
BULK_LOADER_S3_PREFIX = os.environ.get(
    "BULK_LOADER_S3_PREFIX", BULK_LOADER_S3_PREFIX_DEFAULT
)

GRAPH_REMOVER_S3_PREFIX_DEFAULT = "graph_remover"
GRAPH_REMOVER_S3_PREFIX = os.environ.get(
    "GRAPH_REMOVER_S3_PREFIX", GRAPH_REMOVER_S3_PREFIX_DEFAULT
)

INCREMENTAL_GRAPH_REMOVER_S3_PREFIX_DEFAULT = "graph_remover_incremental"
INCREMENTAL_GRAPH_REMOVER_S3_PREFIX = os.environ.get(
    "INCREMENTAL_GRAPH_REMOVER_S3_PREFIX", INCREMENTAL_GRAPH_REMOVER_S3_PREFIX_DEFAULT
)

INGESTOR_SHARD_SIZE_DEFAULT = 10000
INGESTOR_SHARD_SIZE = int(
    os.environ.get("INGESTOR_SHARD_SIZE", INGESTOR_SHARD_SIZE_DEFAULT)
)
INGESTOR_PIPELINE_DATE = os.environ.get("INGESTOR_PIPELINE_DATE")

ES_MERGED_INDEX_NAME = "works-denormalised"

ES_SOURCE_PARALLELISM = 5
ES_SOURCE_SLICE_COUNT = 30
ES_SOURCE_BATCH_SIZE = 2000

## Local Elasticsearch Configuration
ES_LOCAL_HOST = os.environ.get("ES_LOCAL_HOST")
ES_LOCAL_PORT = os.environ.get("ES_LOCAL_PORT")
ES_LOCAL_SCHEME = os.environ.get("ES_LOCAL_SCHEME")
ES_LOCAL_API_KEY = os.environ.get("ES_LOCAL_API_KEY")
