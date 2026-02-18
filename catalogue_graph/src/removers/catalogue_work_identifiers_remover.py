from collections.abc import Generator, Iterable, Iterator

from elasticsearch import Elasticsearch

from clients.neptune_client import NeptuneClient
from models.events import IncrementalGraphRemoverEvent
from models.graph_edge import (
    WorkHasPathIdentifier,
)
from sources.merged_works_source import MergedWorksSource
from transformers.catalogue.raw_work import RawCatalogueWork
from transformers.catalogue.work_identifiers_transformer import ES_FIELDS, ES_QUERY

from .base_graph_remover_incremental import BaseGraphRemoverIncremental


class CatalogueWorkIdentifiersGraphRemover(BaseGraphRemoverIncremental):
    def __init__(
        self,
        event: IncrementalGraphRemoverEvent,
        es_client: Elasticsearch,
        neptune_client: NeptuneClient,
    ):
        super().__init__(event.entity_type, neptune_client)
        self.work_source = MergedWorksSource(
            event, query=ES_QUERY, fields=ES_FIELDS, es_client=es_client
        )

    def get_total_node_count(self) -> int:
        return self.neptune_client.get_total_node_count("PathIdentifier")

    def get_total_edge_count(self) -> int:
        return self.neptune_client.get_total_edge_count("HAS_PATH_IDENTIFIER")

    def get_node_ids_to_remove(self) -> Iterator[str]:
        """Remove the IDs of all path identifier nodes which are not connected to any works"""
        yield from self.neptune_client.get_disconnected_node_ids(
            node_label="PathIdentifier", edge_label="HAS_PATH_IDENTIFIER"
        )

    def get_es_edges(self) -> Generator[tuple[str, set[str]]]:
        """
        Return a dictionary mapping each work ID to a set of path identifier IDs based on data from the merged index.
        """
        for work_document in self.work_source.stream_raw():
            raw_work = RawCatalogueWork(work_document)

            to_ids = set()
            if raw_work.path_identifier:
                edge = WorkHasPathIdentifier(
                    from_id=raw_work.wellcome_id, to_id=raw_work.path_identifier
                )
                to_ids.add(edge.edge_id)

            yield raw_work.wellcome_id, to_ids

    def get_graph_edges(self, start_ids: Iterable[str]) -> dict[str, set[str]]:
        """
        Return a dictionary mapping each work ID to a set of HAS_PATH_IDENTIFIER edge IDs from the catalogue graph.
        """
        return self.neptune_client.get_node_edges(
            start_ids, edge_label="HAS_PATH_IDENTIFIER"
        )
