from elasticsearch import Elasticsearch

import config
from core.source import ElasticSource
from models.events import BasePipelineEvent
from utils.elasticsearch import get_images_augmented_index_name


class AugmentedImagesSource(ElasticSource):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        query: dict | None = None,
        fields: list | None = None,
    ):
        super().__init__(
            es_client=es_client,
            index_name=get_images_augmented_index_name(event),
            pit_id=event.pit_id,
            query=event.to_elasticsearch_query("modifiedTime", query),
            fields=fields,
            batch_size=config.ES_SOURCE_BATCH_SIZE,
            slice_count=config.ES_SOURCE_SLICE_COUNT,
            parallelism=config.ES_SOURCE_PARALLELISM,
        )
