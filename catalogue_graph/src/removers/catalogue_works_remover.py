from collections.abc import Iterator

from removers.base_remover import BaseGraphRemover
from sources.merged_works_source import MergedWorksSource
from utils.elasticsearch import ElasticsearchMode

ES_QUERY = {"bool": {"must_not": {"match": {"type": "Visible"}}}}


class CatalogueWorksGraphRemover(BaseGraphRemover):
    def __init__(self, event, es_mode: ElasticsearchMode):
        super().__init__(event.entity_type, es_mode != "private")
        self.es_source = MergedWorksSource(
            event,
            query=ES_QUERY,
            fields=["state.canonicalId"],
            es_mode=es_mode,
        )

    def get_node_ids_to_remove(self) -> Iterator[str]:
        """
        Return the ids of all works which are not 'Visible' and which were modified within the specified time window.
        """
        for work in self.es_source.stream_raw():
            yield work["state"]["canonicalId"]

    def get_edge_ids_to_remove(self) -> Iterator[str]:
        yield from ()
