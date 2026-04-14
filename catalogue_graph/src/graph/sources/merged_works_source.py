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
    ):
        super().from_document_selection(
            es_client=es_client,
            index_name=get_merged_index_name(event),
            document_selection=event,
            range_filter_field_name="state.mergedTime",
            query=query,
            pit_id=event.pit_id,
            fields=fields,
        )
