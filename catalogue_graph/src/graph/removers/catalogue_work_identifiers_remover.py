from collections import defaultdict
from collections.abc import Iterable, Iterator
from itertools import batched

from elasticsearch import Elasticsearch

from clients.neptune_client import NeptuneClient
from graph.sources.merged_works_source import MergedWorksSource
from graph.transformers.catalogue.raw_work import RawCatalogueWork
from graph.transformers.catalogue.work_identifiers_transformer import (
    ES_FIELDS,
    ES_QUERY,
)
from models.events import IncrementalGraphRemoverEvent, PipelinePitIds
from models.graph_edge import (
    PathIdentifierHasParent,
    WorkHasPathIdentifier,
)
from utils.safety import validate_fractional_change

from .base_graph_remover_incremental import BATCH_SIZE, BaseGraphRemoverIncremental

HAS_PARENT_EDGE_ID_PREFIX = "HAS_PARENT:"
WORK_LOOKUP_BATCH_SIZE = 1000


def _get_path_identifier_edge_ids(work: RawCatalogueWork) -> set[str]:
    if work.path_identifier is None:
        return set()

    edge = WorkHasPathIdentifier(from_id=work.wellcome_id, to_id=work.path_identifier)
    return {edge.edge_id}


def _get_parent_edge_ids(work: RawCatalogueWork) -> set[str]:
    if work.path_identifier is None or work.parent_path_identifier is None:
        return set()

    edge = PathIdentifierHasParent(
        from_id=work.path_identifier, to_id=work.parent_path_identifier
    )
    return {edge.edge_id}


class CatalogueWorkIdentifiersGraphRemover(BaseGraphRemoverIncremental):
    def __init__(
        self,
        event: IncrementalGraphRemoverEvent,
        es_client: Elasticsearch,
        neptune_client: NeptuneClient,
    ):
        super().__init__(event.entity_type, neptune_client)
        self.event = event
        self.es_client = es_client
        self.work_source = MergedWorksSource(
            event, query=ES_QUERY, fields=ES_FIELDS, es_client=es_client
        )

    def get_total_node_count(self) -> int:
        return self.neptune_client.get_total_node_count("PathIdentifier")

    def get_total_edge_count(self) -> int:
        return self.neptune_client.get_total_edge_count("HAS_PATH_IDENTIFIER")

    def validate_removal(self, ids: list[str], force_pass: bool) -> None:
        if self.entity_type == "nodes":
            super().validate_removal(ids, force_pass)
            return

        # Check each edge type against its own total, so a large HAS_PARENT removal is not diluted
        parent_edge_count = sum(i.startswith(HAS_PARENT_EDGE_ID_PREFIX) for i in ids)
        validate_fractional_change(
            modified_size=len(ids) - parent_edge_count,
            total_size=self.get_total_edge_count(),
            force_pass=force_pass,
        )
        validate_fractional_change(
            modified_size=parent_edge_count,
            total_size=self.neptune_client.get_total_edge_count(
                "HAS_PARENT", source_label="PathIdentifier"
            ),
            force_pass=force_pass,
        )

    def get_node_ids_to_remove(self) -> Iterator[str]:
        """Remove the IDs of all path identifier nodes which are not connected to any works"""
        yield from self.neptune_client.get_disconnected_node_ids(
            node_label="PathIdentifier", edge_label="HAS_PATH_IDENTIFIER"
        )

    def get_edge_ids_to_remove(self) -> Iterator[str]:
        """Return stale HAS_PATH_IDENTIFIER and HAS_PARENT edges of the works in scope."""
        # A node shared by works in different batches is reconciled once per batch
        yielded_parent_edge_ids: set[str] = set()
        for batch in batched(self.work_source.stream_raw(), BATCH_SIZE):
            works = [RawCatalogueWork(document) for document in batch]
            yield from self._get_stale_path_identifier_edge_ids(works)
            for edge_id in self._get_stale_parent_edge_ids(works):
                if edge_id not in yielded_parent_edge_ids:
                    yielded_parent_edge_ids.add(edge_id)
                    yield edge_id

    def _get_stale_path_identifier_edge_ids(
        self, works: list[RawCatalogueWork]
    ) -> Iterator[str]:
        es_edges = {w.wellcome_id: _get_path_identifier_edge_ids(w) for w in works}
        graph_edges = self.neptune_client.get_node_edges(
            es_edges.keys(), edge_label="HAS_PATH_IDENTIFIER"
        )
        for work_id, graph_edge_ids in graph_edges.items():
            yield from graph_edge_ids.difference(es_edges.get(work_id, set()))

    def _get_stale_parent_edge_ids(
        self, works: list[RawCatalogueWork]
    ) -> Iterator[str]:
        # Aggregated per node, because several works can share a path identifier
        expected_edges: dict[str, set[str]] = defaultdict(set)
        for work in works:
            if work.path_identifier is not None:
                expected_edges[work.path_identifier] |= _get_parent_edge_ids(work)

        # Directed and label-restricted, so children's edges and concept HAS_PARENT edges are never included
        graph_edges = self.neptune_client.get_node_edges(
            expected_edges.keys(),
            edge_label="HAS_PARENT",
            node_label="PathIdentifier",
            outgoing_only=True,
        )
        candidates = {
            node_id: stale_edge_ids
            for node_id, edge_ids in graph_edges.items()
            if (stale_edge_ids := edge_ids - expected_edges.get(node_id, set()))
        }
        if not candidates:
            return

        # Works outside this batch or window can share a node, and their parent edges must stay
        batch_work_ids = {w.wellcome_id for w in works}
        linked_work_ids = self.neptune_client.get_source_node_ids(
            candidates.keys(),
            edge_label="HAS_PATH_IDENTIFIER",
            node_label="PathIdentifier",
            source_label="Work",
        )
        other_work_ids = set().union(*linked_work_ids.values()) - batch_work_ids
        for work in self._get_works_by_id(other_work_ids):
            if work.path_identifier in candidates:
                candidates[work.path_identifier] -= _get_parent_edge_ids(work)

        for stale_edge_ids in candidates.values():
            yield from stale_edge_ids

    def _get_works_by_id(self, work_ids: Iterable[str]) -> Iterator[RawCatalogueWork]:
        for chunk in batched(sorted(work_ids), WORK_LOOKUP_BATCH_SIZE):
            unscoped_event = self.event.model_copy(
                update={
                    "window": None,
                    "ids": list(chunk),
                    "pit_ids": PipelinePitIds(merged=self.work_source.pit_id),
                }
            )
            source = MergedWorksSource(
                unscoped_event,
                es_client=self.es_client,
                query=ES_QUERY,
                fields=ES_FIELDS,
                slice_count=1,
            )
            for document in source.stream_raw():
                yield RawCatalogueWork(document)
