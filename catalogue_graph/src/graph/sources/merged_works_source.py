from elasticsearch import Elasticsearch

from core.source import ElasticSource
from models.events import BasePipelineEvent
from utils.elasticsearch import get_merged_index_name

# Stamped by the index's ingest pipeline when a work is written; state.mergedTime is
# the matcher's time, which a merger backlog can push behind an already-processed window
MERGED_WINDOW_FIELD = "indexed_at"
# For works in an index that doesn't stamp indexed_at
MERGED_WINDOW_FALLBACK_FIELD = "state.mergedTime"


class MergedWorksSource(ElasticSource):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        query: dict | None = None,
        fields: list | None = None,
        slice_count: int | None = None,
    ):
        super().__init__(
            es_client=es_client,
            index_name=get_merged_index_name(event),
            query=event.to_elasticsearch_query(
                MERGED_WINDOW_FIELD, query, MERGED_WINDOW_FALLBACK_FIELD
            ),
            pit_id=event.pit_ids.merged,
            fields=fields,
            slice_count=slice_count or event.slice_count,
        )
