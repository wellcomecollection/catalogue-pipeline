import elasticsearch
from pydantic import BaseModel

from config import (
    INGESTOR_ES_API_KEY,
    INGESTOR_ES_HOST,
    INGESTOR_ES_PORT,
    INGESTOR_ES_SCHEME,
)
from utils.aws import get_secret


class ElasticsearchConfig(BaseModel):
    host: str = "localhost" if INGESTOR_ES_HOST is None else INGESTOR_ES_HOST
    port: int = 9200 if INGESTOR_ES_PORT is None else int(INGESTOR_ES_PORT)
    scheme: str = "http" if INGESTOR_ES_SCHEME is None else INGESTOR_ES_SCHEME
    apikey: str | None = INGESTOR_ES_API_KEY


class ElasticsearchClient:
    def __init__(self, config: ElasticsearchConfig) -> None:
        host_config = f"{config.scheme}://{config.host}:{config.port}"
        print(f"Creating Elasticsearch client for {host_config}")
        self.client = elasticsearch.Elasticsearch(host_config, api_key=config.apikey)


def get_pipeline_config(pipeline_date: str, is_local: bool) -> ElasticsearchConfig:
    es_host_type = "public_host" if is_local else "private_host"

    return ElasticsearchConfig(
        host=get_secret(
            f"elasticsearch/pipeline_storage_{pipeline_date}/{es_host_type}"
        ),
        port=int(get_secret(f"elasticsearch/pipeline_storage_{pipeline_date}/port")),
        scheme=get_secret(f"elasticsearch/pipeline_storage_{pipeline_date}/protocol"),
        apikey=get_secret(
            f"elasticsearch/pipeline_storage_{pipeline_date}/concept_ingestor/api_key"
        ),
    )


def get_client(
    pipeline_date: str | None = None, is_local: bool = False
) -> elasticsearch.Elasticsearch:
    config = (
        ElasticsearchConfig()
        if pipeline_date is None or pipeline_date == "dev"
        else get_pipeline_config(pipeline_date, is_local)
    )
    return ElasticsearchClient(config).client
