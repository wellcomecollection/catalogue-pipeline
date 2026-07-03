from queue import Queue

from elasticsearch import Elasticsearch

import config
from core.source import ElasticSource
from models.events import BasePipelineEvent
from utils.elasticsearch import get_images_initial_index_name


class ImagesInitialSource(ElasticSource):
    """Streams the _ids of images-initial docs matching the event's time window.

    Uses the shared ElasticSource (PIT + search_after, retries, slice
    parallelism); we only need _id, so _source is disabled and worker_target
    yields the document id.
    """

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

    def worker_target(self, slice_index: int, queue: Queue) -> None:
        search_after = None
        while hits := self.search(slice_index, search_after):
            for hit in hits:
                queue.put(hit["_id"])
            search_after = hits[-1]["sort"]
        queue.put(None)
