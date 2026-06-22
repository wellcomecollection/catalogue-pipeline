import json
import os
from collections.abc import Generator, Sequence
from typing import Any, Literal, cast

import backoff
import elasticsearch
import elasticsearch.helpers
import structlog
from elasticsearch import Elasticsearch
from pydantic import BaseModel

from config import (
    ES_IMAGES_AUGMENTED_INDEX_NAME,
    ES_IMAGES_INITIAL_INDEX_NAME,
    ES_LOCAL_API_KEY,
    ES_LOCAL_HOST,
    ES_LOCAL_PORT,
    ES_LOCAL_SCHEME,
    ES_MERGED_INDEX_NAME,
)
from ingestor.models.indexable.record import IndexableRecord
from models.events import BasePipelineEvent
from utils.aws import get_secret

logger = structlog.get_logger(__name__)

# Transient transport failures talking to Elasticsearch (e.g. a dropped keep-alive
# connection -> "Remote end closed connection without response"). These are not
# data errors and clear on a retry; without retrying, a single blip during a bulk
# write fails the whole all-or-nothing inference task (and can abort the run).
TRANSIENT_ES_ERRORS = (
    elasticsearch.exceptions.ConnectionError,
    elasticsearch.exceptions.ConnectionTimeout,
)
ES_BULK_MAX_ATTEMPTS = int(os.environ.get("ES_BULK_MAX_ATTEMPTS", "4"))
ES_BULK_BACKOFF_SECONDS = float(os.environ.get("ES_BULK_BACKOFF_SECONDS", "1.0"))


def _on_bulk_backoff(backoff_details: Any) -> None:
    exception_name = type(backoff_details["exception"]).__name__
    logger.warning(
        "Transient Elasticsearch error during bulk write, retrying",
        exception_name=exception_name,
        attempt=backoff_details["tries"],
        max_attempts=ES_BULK_MAX_ATTEMPTS,
    )


# private: Connect to the production cluster via the private endpoint (production runs only)
# public: Connect to the production cluster via the public endpoint (local runs only)
# local: Connect to a local (dev) instance (local runs only)
ElasticsearchMode = Literal["private", "public", "local"]


def get_standard_index_name(prefix: str, date: str | None) -> str:
    if date is not None:
        return f"{prefix}-{date}"

    return prefix


def get_merged_index_name(event: BasePipelineEvent) -> str:
    index_date = event.index_dates.merged or event.pipeline_date
    return get_standard_index_name(ES_MERGED_INDEX_NAME, index_date)


def get_images_augmented_index_name(event: BasePipelineEvent) -> str:
    index_date = event.index_dates.augmented or event.pipeline_date
    return get_standard_index_name(ES_IMAGES_AUGMENTED_INDEX_NAME, index_date)


def get_images_initial_index_name(event: BasePipelineEvent) -> str:
    # Defaults to `images-initial-<pipeline_date>` (where the merger writes), but
    # can be pointed at a shadow source index via `index_dates.initial` (which
    # indexes `modifiedTime` so the inferrer's time-window query works).
    index_date = event.index_dates.initial or event.pipeline_date
    return get_standard_index_name(ES_IMAGES_INITIAL_INDEX_NAME, index_date)


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
    logger.info(
        "Creating Elasticsearch client", es_mode=es_mode, host_config=host_config
    )
    return elasticsearch.Elasticsearch(host_config, api_key=config.apikey, timeout=60)


@backoff.on_exception(
    backoff.expo,
    TRANSIENT_ES_ERRORS,
    max_tries=lambda: ES_BULK_MAX_ATTEMPTS,
    factor=ES_BULK_BACKOFF_SECONDS,
    on_backoff=_on_bulk_backoff,
)
def index_es_batch(
    es_client: Elasticsearch, es_actions: list[dict]
) -> tuple[int, list[dict[str, Any]]]:
    success_count, es_errors = elasticsearch.helpers.bulk(
        es_client,
        es_actions,
        raise_on_error=False,
        stats_only=False,
    )

    logger.info(
        "Indexed batch",
        success_count=success_count,
        batch_size=len(es_actions),
    )

    # Since we called `bulk` with `stats_only=False`, we know that es_errors is a list of dicts
    es_errors = cast(list[dict[str, Any]], es_errors)
    return success_count, es_errors


def generate_operations(
    index_name: str, indexable_data: Sequence[IndexableRecord]
) -> Generator[dict]:
    for datum in indexable_data:
        source = json.loads(datum.model_dump_json(exclude_none=True))
        version = int(datum.get_modified_time().timestamp() * 1000)  # epoch millis

        # Documents whose modified date is set to the start of the Unix epoch will
        # have a version of 0. We floor this to 100 for backward compatibility with
        # documents which use Elasticsearch's default versioning (which increments
        # every time a given document is reindexed). This won't be needed after we
        # do a full reindex.
        version = max(100, version)

        yield {
            "_index": index_name,
            "_id": datum.get_id(),
            "_source": source,
            "_version": version,
            "_version_type": "external_gte",
        }
