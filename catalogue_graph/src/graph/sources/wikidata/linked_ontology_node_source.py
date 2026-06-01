from collections.abc import Generator

from .linked_ontology_source import HAS_PARENT_EDGE_TYPES, WikidataLinkedOntologySource
from .sparql_query_builder import SparqlQueryBuilder


class WikidataLinkedOntologyNodeSource(WikidataLinkedOntologySource):
    """Streams Wikidata nodes linked to a selected ontology."""

    def _stream_filtered_wikidata_ids(self) -> Generator[str]:
        """Streams all Wikidata IDs to be processed as nodes given the selected `node_type`."""
        seen = set()

        # Retrieve all SAME_AS edges connecting Wikidata IDs with IDs from the linked ontology.
        for edge in self._stream_all_edges_by_type(self.same_as_edge_type):
            wikidata_id, linked_id = edge["from_id"], edge["to_id"]
            is_linked_id_valid = self._is_id_valid_for_ontology(
                linked_id, self.linked_ontology
            )

            # Deduplicate, since a given Wikidata ID can appear in more than one edge.
            if is_linked_id_valid and wikidata_id not in seen:
                # Add Wikidata ID to `seen` regardless of whether it's classified under the selected node type
                # to make sure it is not processed again as a parent below.
                seen.add(wikidata_id)

                # Verify that the linked ID is valid by checking that it was processed by the relevant transformer.
                if self._is_id_valid_for_transformer(
                    linked_id, self.linked_transformer
                ):
                    yield wikidata_id

        # Yield all unseen parents of the nodes yielded above. All parents are categorised as _concepts_,
        # no matter whether their children are concepts, names, or locations.
        if self.node_type == "concepts":
            for edge_type in HAS_PARENT_EDGE_TYPES:
                for edge in self._stream_all_edges_by_type(edge_type):
                    parent_wikidata_id = edge["to_id"]
                    if parent_wikidata_id not in seen:
                        seen.add(parent_wikidata_id)
                        yield parent_wikidata_id

    def stream_raw(self) -> Generator[dict]:
        """
        Extract nodes via the following steps:
            1. Stream raw edges and extract Wikidata IDs from them.
            2. Split the extracted IDs into chunks. For each chunk, run a SPARQL query to retrieve all the corresponding
            Wikidata fields required to create a node.
        """

        def build_items_query(wikidata_ids: list[str]) -> str:
            return SparqlQueryBuilder.get_items_query(wikidata_ids, self.node_type)

        all_ids = self._stream_filtered_wikidata_ids()
        yield from self.client.run_query_in_parallel(all_ids, build_items_query)
