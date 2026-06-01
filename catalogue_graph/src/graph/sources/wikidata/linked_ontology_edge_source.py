from collections.abc import Generator

from .linked_ontology_source import (
    HAS_PARENT_EDGE_TYPES,
    PEOPLE_RELATIONSHIP_EDGE_TYPES,
    WikidataLinkedOntologySource,
)
from .sparql_query_builder import WikidataEdgeQueryType


class WikidataLinkedOntologyEdgeSource(WikidataLinkedOntologySource):
    """Streams Wikidata edges linked to a selected ontology."""

    def _stream_all_edges_by_type(
        self, edge_type: WikidataEdgeQueryType
    ) -> Generator[dict]:
        # Only keep edges whose start node was extracted by the current transformer
        # (e.g. "wikidata_linked_loc_concepts"), discarding edges from unrelated node types.
        # (This makes the Wikidata edge transformer dependent on the node transformer,
        # so we need to make sure that the node transformer always runs first.)
        for edge in super()._stream_all_edges_by_type(edge_type):
            if self._is_id_valid_for_transformer(
                edge["from_id"], self.event.transformer_type
            ):
                yield edge

    def stream_raw(self) -> Generator[tuple[dict, WikidataEdgeQueryType]]:
        """
        Stream edges for the selected `linked_ontology` and `node_type`. Yield tuples of (edge_dict, edge_type).
        """

        # SAME_AS edges map a Wikidata ID to its equivalent in the linked ontology (LoC/MeSH).
        for edge in self._stream_all_edges_by_type(self.same_as_edge_type):
            # Only include edges where the target ID belongs to the current `node_type` in the linked ontology.
            # For example, when processing "loc_names", skip edges pointing to LoC IDs classified as locations/concepts.
            # This also filters out invalid linked IDs (of which there are several thousand).
            if self._is_id_valid_for_transformer(
                edge["to_id"], self.linked_transformer
            ):
                yield edge, self.same_as_edge_type

        edge_types: list[WikidataEdgeQueryType] = [
            *HAS_PARENT_EDGE_TYPES,
            "has_industry",
            "has_founder",
        ]
        if self.node_type == "names":
            edge_types += ["has_field_of_work", *PEOPLE_RELATIONSHIP_EDGE_TYPES]

        # Remaining edges are internal to Wikidata (both endpoints are Wikidata nodes).
        for edge_type in edge_types:
            for edge in self._stream_all_edges_by_type(edge_type):
                # Keep only edges whose target is a known Wikidata node (extracted by any wikidata transformer,
                # regardless of `node_type`).
                if self._is_id_valid_for_ontology(edge["to_id"], "wikidata"):
                    yield edge, edge_type
