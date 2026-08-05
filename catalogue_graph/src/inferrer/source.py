from elasticsearch import Elasticsearch

import config
from core.source import ElasticIdsSource
from models.events import BasePipelineEvent
from utils.elasticsearch import get_images_initial_index_name


class ImagesInitialSource(ElasticIdsSource):
    """Streams the _ids of images-initial docs matching the event's time window."""

    def __init__(self, event: BasePipelineEvent, es_client: Elasticsearch):
        super().__init__(
            es_client=es_client,
            index_name=get_images_initial_index_name(event),
            query=event.to_elasticsearch_query("modifiedTime"),
            fields=[],
            batch_size=config.ES_SOURCE_BATCH_SIZE,
            slice_count=config.ES_SOURCE_SLICE_COUNT,
            parallelism=config.ES_SOURCE_PARALLELISM,
        )
