from typing import Literal

import elasticsearch
from pydantic import BaseModel

from config import (
    ES_LOCAL_API_KEY,
    ES_LOCAL_HOST,
    ES_LOCAL_PORT,
    ES_LOCAL_SCHEME,
)
from utils.aws import get_secret

# private: Connect to the production cluster via the private endpoint (production runs only)
# public: Connect to the production cluster via the public endpoint (local runs only)
# local: Connect to a local (dev) instance (local runs only)
ElasticsearchMode = Literal["private", "public", "local"]


def get_standard_index_name(prefix: str, date: str | None) -> str:
    if date is not None:
        return f"{prefix}-{date}"

    return prefix


class ElasticsearchConfig(BaseModel):
    host: str = "localhost" if ES_LOCAL_HOST is None else ES_LOCAL_HOST
    port: int = 9200 if ES_LOCAL_PORT is None else int(ES_LOCAL_PORT)
    scheme: str = "http" if ES_LOCAL_SCHEME is None else ES_LOCAL_SCHEME
    apikey: str | None = ES_LOCAL_API_KEY


def get_pipeline_config(
    pipeline_date: str, es_mode: ElasticsearchMode, api_key_name: str
) -> ElasticsearchConfig:
    if es_mode == "local":
        return ElasticsearchConfig()

    secret_prefix = f"elasticsearch/pipeline_storage_{pipeline_date}"

    return ElasticsearchConfig(
        host=get_secret(f"{secret_prefix}/{es_mode}_host"),
        port=int(get_secret(f"{secret_prefix}/port")),
        scheme=get_secret(f"{secret_prefix}/protocol"),
        apikey=get_secret(f"{secret_prefix}/{api_key_name}/api_key"),
    )


def get_client(
    api_key_name: str, pipeline_date: str, es_mode: ElasticsearchMode = "private"
) -> elasticsearch.Elasticsearch:
    config = get_pipeline_config(pipeline_date, es_mode, api_key_name)

    host_config = f"{config.scheme}://{config.host}:{config.port}"
    print(f"Creating Elasticsearch client in '{es_mode}' mode ({host_config})")
    return elasticsearch.Elasticsearch(host_config, api_key=config.apikey, timeout=60)
