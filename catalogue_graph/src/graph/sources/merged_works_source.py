from elasticsearch import Elasticsearch

import config
from core.source import ElasticSource
from models.events import BasePipelineEvent, IncrementalWindow
from utils.elasticsearch import get_merged_index_name


def build_merged_index_query(
    query: dict | None,
    window: IncrementalWindow | None,
) -> dict:
    full_query = {"match_all": {}} if query is None else query
    if window is not None:
        range_filter = window.to_elasticsearch_filter(field_name="state.mergedTime")
        full_query = {"bool": {"must": [full_query, range_filter]}}

    return full_query


class MergedWorksSource(ElasticSource):
    def __init__(
        self,
        event: BasePipelineEvent,
        es_client: Elasticsearch,
        query: dict | None = None,
        fields: list | None = None,
    ):
        super().__init__(
            es_client=es_client,
            index_name=get_merged_index_name(event),
            query=build_merged_index_query(query, event.window),
            pit_id=event.pit_id,
            fields=fields,
            batch_size=config.ES_SOURCE_BATCH_SIZE,
            slice_count=config.ES_SOURCE_SLICE_COUNT,
            parallelism=config.ES_SOURCE_PARALLELISM,
        )
