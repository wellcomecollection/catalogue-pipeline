from elasticsearch import Elasticsearch

import config
from core.source import ElasticIdsSource, ElasticSource
from models.source_scope import SourceScope


class IdMintingSource(ElasticSource):
    """Fetches work documents from a works-source index."""

    def __init__(
        self, source_scope: SourceScope, es_client: Elasticsearch, index_name: str
    ):
        query = source_scope.to_elasticsearch_query(
            range_filter_field_name="indexed_at"
        )

        super().__init__(
            es_client=es_client,
            index_name=index_name,
            query=query,
            slice_count=source_scope.slice_count,
        )


class MintingWorkIdsSource(ElasticIdsSource):
    """Streams only the _ids of works-source docs in scope (for find_work)."""

    def __init__(
        self, source_scope: SourceScope, es_client: Elasticsearch, index_name: str
    ):
        super().__init__(
            es_client=es_client,
            index_name=index_name,
            query=source_scope.to_elasticsearch_query(
                range_filter_field_name="indexed_at"
            ),
            fields=[],
            batch_size=config.ES_SOURCE_BATCH_SIZE,
            slice_count=source_scope.slice_count,
            parallelism=config.ES_SOURCE_PARALLELISM,
        )
