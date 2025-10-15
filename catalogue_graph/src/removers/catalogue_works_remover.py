from collections.abc import Generator, Iterable, Iterator

from models.events import IncrementalRemoverEvent
from sources.catalogue.concepts_source import ES_FIELDS as ES_FIELDS_WORK_CONCEPTS
from sources.catalogue.concepts_source import ES_QUERY as ES_QUERY_WORK_CONCEPTS
from sources.merged_works_source import MergedWorksSource
from transformers.catalogue.raw_work import RawCatalogueWork
from utils.elasticsearch import ElasticsearchMode

from .base_remover import BaseGraphRemover

ES_QUERY_NON_VISIBLE = {"bool": {"must_not": {"match": {"type": "Visible"}}}}


class CatalogueWorksGraphRemover(BaseGraphRemover):
    def __init__(self, event: IncrementalRemoverEvent, es_mode: ElasticsearchMode):
        super().__init__(event.entity_type, es_mode != "private")
        self.non_visible_works_source = MergedWorksSource(
            event,
            query=ES_QUERY_NON_VISIBLE,
            fields=["state.canonicalId"],
            es_mode=es_mode,
        )
        self.work_concepts_source = MergedWorksSource(
            event,
            query=ES_QUERY_WORK_CONCEPTS,
            fields=ES_FIELDS_WORK_CONCEPTS,
            es_mode=es_mode,
        )

    def get_node_ids_to_remove(self) -> Iterator[str]:
        """
        Return the ids of all works which are not 'Visible' and which were modified within the specified time window.
        """
        for work in self.non_visible_works_source.stream_raw():
            yield work["state"]["canonicalId"]

    def get_es_edges(self) -> Generator[tuple[str, set[str]]]:
        """Return a dictionary mapping each work ID to a set of concept IDs based on data from the merged index."""
        for work_document in self.work_concepts_source.stream_raw():
            raw_work = RawCatalogueWork(work_document)
            concept_ids = [concept.id for concept in raw_work.concepts]
            yield raw_work.wellcome_id, set(concept_ids)

    def get_graph_edges(self, work_ids: Iterable[str]) -> dict[str, set[str]]:
        """Return a dictionary mapping each work ID to a set of HAS_CONCEPT edge IDs from the catalogue graph."""
        return self.neptune_client.get_node_edges(work_ids, edge_label="HAS_CONCEPT")
