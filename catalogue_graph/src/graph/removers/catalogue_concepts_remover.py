from collections import defaultdict
from collections.abc import Generator, Iterable, Iterator

from clients.neptune_client import NeptuneClient
from models.events import ExtractorEvent, IncrementalGraphRemoverEvent
from utils.aws import get_csv_from_s3
from utils.types import EntityType

from .base_graph_remover_incremental import BaseGraphRemoverIncremental


class CatalogueConceptsGraphRemover(BaseGraphRemoverIncremental):
    def __init__(
        self, event: IncrementalGraphRemoverEvent, neptune_client: NeptuneClient
    ):
        super().__init__(event.entity_type, neptune_client)
        self.event = event

    def get_total_node_count(self) -> int:
        return self.neptune_client.get_total_node_count("Concept")

    def get_total_edge_count(self) -> int:
        return self.neptune_client.get_total_edge_count(
            "HAS_SOURCE_CONCEPT", source_label="Concept"
        )

    def get_node_ids_to_remove(self) -> Iterator[str]:
        """Remove the IDs of all concept nodes which are not connected to any works"""
        yield from self.neptune_client.get_disconnected_node_ids(
            node_label="Concept", edge_label="HAS_CONCEPT"
        )

    def _full_extract_uri(self, entity_type: EntityType) -> str:
        # The latest full extract's bulk-load files are the expected state of the graph
        extractor_event = ExtractorEvent(
            transformer_type="catalogue_concepts",
            entity_type=entity_type,
            pipeline_date=self.event.pipeline_date,
            graph_date=self.event.graph_date,
        )
        return extractor_event.get_s3_uri()

    def get_es_edges(self) -> Generator[tuple[str, set[str]]]:
        """Return the HAS_SOURCE_CONCEPT edges each extracted concept should have, including concepts with none."""
        expected_edges: dict[str, set[str]] = defaultdict(set)
        for row in get_csv_from_s3(self._full_extract_uri("edges")):
            expected_edges[row[":START_ID"]].add(row[":ID"])

        for row in get_csv_from_s3(self._full_extract_uri("nodes")):
            concept_id = row[":ID"]
            yield concept_id, expected_edges.pop(concept_id, set())

        # Concepts only present in the edges file still need their graph edges checked
        yield from expected_edges.items()

    def get_graph_edges(self, concept_ids: Iterable[str]) -> dict[str, set[str]]:
        return self.neptune_client.get_node_edges(
            concept_ids,
            edge_label="HAS_SOURCE_CONCEPT",
            node_label="Concept",
            outgoing_only=True,
        )

    def get_edge_ids_to_remove(self) -> Iterator[str]:
        # A window extracts only some works, so its edges cannot say which graph edges are stale
        if self.event.window is not None:
            return iter(())

        return super().get_edge_ids_to_remove()
