from elasticsearch import Elasticsearch

from core.source import ElasticSource
from models.events import BasePipelineEvent
from utils.elasticsearch import get_merged_index_name


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
            query=event.to_elasticsearch_query("state.mergedTime", query),
            pit_id=event.pit_ids.merged,
            fields=fields,
            slice_count=slice_count or event.slice_count,
        )
