from collections.abc import Generator, Iterator
from typing import Callable

from sources.base_source import BaseSource
from transformers.base_transformer import EntityType
from utils.streaming import process_stream_in_parallel
from functools import lru_cache

from .linked_ontology_id_type_checker import LinkedOntologyIdTypeChecker
from .sparql_client import MAX_PARALLEL_SPARQL_QUERIES, WikidataSparqlClient
from .sparql_query_builder import NodeType, OntologyType, SparqlQueryBuilder

SPARQL_ITEMS_CHUNK_SIZE = 400

WIKIDATA_ID_PREFIX = "http://www.wikidata.org/entity/"


def extract_wikidata_id(item: dict, key: str = "item") -> str | None:
    """
    Accepts a raw `item` dictionary returned by the Wikidata SPARQL endpoint and returns the Wikidata id of the item.
    Returns `None` if the stored id is not valid.
    """
    wikidata_id = item[key]["value"]
    assert isinstance(wikidata_id, str)
    assert item[key]["type"] == "uri"

    if wikidata_id.startswith(WIKIDATA_ID_PREFIX):
        return wikidata_id.removeprefix(WIKIDATA_ID_PREFIX)

    # Very rarely, Wikidata returns an invalid ID in the format
    # http://www.wikidata.org/.well-known/genid/<some hexadecimal string>.
    # Log when this happens and return 'None'.
    print(f"Encountered an invalid Wikidata id: {wikidata_id}")
    return None


class WikidataLinkedOntologySource(BaseSource):
    """
    A source for streaming selected Wikidata nodes/edges. There are _many_ Wikidata items, so we cannot store all of
    them in the graph. Instead, we only include items which reference an id from a selected linked ontology
    (LoC or MeSH) and their parents.

    Wikidata puts strict limits on the resources which can be consumed by a single query, and queries which include
    filters or do other expensive processing often time out or return a stack overflow error. This means we need
    to use a somewhat convoluted way for extracting the Wikidata nodes/edges we need.
    See https://www.wikidata.org/wiki/Wikidata:SPARQL_query_service/query_optimization for more information on how
    to optimise SPARQL queries.
    """

    def __init__(
        self,
        node_type: NodeType,
        linked_ontology: OntologyType,
        entity_type: EntityType,
    ):
        self.client = WikidataSparqlClient()
        self.node_type = node_type
        self.linked_ontology = linked_ontology
        self.entity_type = entity_type
        self.id_type_checker = LinkedOntologyIdTypeChecker(node_type, linked_ontology)

    @lru_cache
    def _get_all_ids(self) -> list[str]:
        """
        Return all Wikidata ids corresponding to Wikidata items referencing the selected linked ontology.
        All ids are returned, no matter whether we categorise them as concepts, names, or locations.
        """
        print(
            f"Retrieving Wikidata ids linked to {self.linked_ontology} items.",
            end=" ",
            flush=True,
        )
        ids_query = SparqlQueryBuilder.get_all_ids_query(self.linked_ontology)
        id_items = self.client.run_query(ids_query)

        # Deduplicate. (We could deduplicate as part of the SPARQL query via the 'DISTINCT' keyword,
        # but that would make the query significantly slower. It's faster to deduplicate here.)
        all_ids = set(extract_wikidata_id(item) for item in id_items)
        all_valid_ids = [i for i in all_ids if i is not None]

        print(f"({len(all_valid_ids)} ids retrieved.)")
        return list(all_valid_ids)

    def _get_linked_id_mappings(self, wikidata_ids: list[str]) -> list[dict]:
        query = SparqlQueryBuilder.get_linked_ids_query(
            wikidata_ids, self.linked_ontology
        )
        return self.client.run_query(query)

    def _get_wikidata_items(self, wikidata_ids: list[str]) -> list:
        query = SparqlQueryBuilder.get_items_query(wikidata_ids, self.node_type)
        return self.client.run_query(query)

    def _get_parent_id_mappings(self, child_wikidata_ids: list[str]) -> list[dict]:
        """
        Given a list of child wikidata ids, checks for all parents of each item in the list and returns a list
        of mappings between child and parent ids.
        """
        # Get all parent ids referenced via the Wikidata 'subclass of' field
        subclass_of_query = SparqlQueryBuilder.get_parents_query(
            child_wikidata_ids, "subclass_of"
        )
        subclass_of_results = self.client.run_query(subclass_of_query)

        # Get all parent ids referenced via the Wikidata 'instance of' field
        instance_of_query = SparqlQueryBuilder.get_parents_query(
            child_wikidata_ids, "instance_of"
        )
        instance_of_results = self.client.run_query(instance_of_query)

        return subclass_of_results + instance_of_results

    @staticmethod
    def _parallelise_requests(
        items: Iterator, run_sparql_query: Callable[[list], list]
    ) -> Generator:
        """Accept an `items` generator and a `run_sparql_query` method. Split `items` chunks and apply
        `run_sparql_query` to each chunk. Return a single generator of results."""
        for raw_response_item in process_stream_in_parallel(
            items,
            run_sparql_query,
            SPARQL_ITEMS_CHUNK_SIZE,
            MAX_PARALLEL_SPARQL_QUERIES,
        ):
            yield raw_response_item

    def _stream_filtered_wikidata_ids(self) -> Generator[str]:
        """Streams all wikidata ids to be processed as nodes given the selected `node_type`."""
        seen = set()

        # Stream all SAME_AS edges and extract Wikidata ids from them, making sure to deduplicate
        # (a given Wikidata id can appear in more than one edge).
        for item in self._stream_all_same_as_edges():
            wikidata_id = item["wikidata_id"]
            linked_id = item["linked_id"]
            if self.id_type_checker.id_is_valid(linked_id) and wikidata_id not in seen:
                seen.add(wikidata_id)

                if self.id_type_checker.id_included_in_selected_type(linked_id):
                    yield wikidata_id

        # Stream HAS_PARENT edges and extract Wikidata ids of all parents (children are streamed above). Filter out
        # all parent ids which reference a linked ontology ids. All remaining ids belong to items which do not
        # reference a MeSH/LoC id. We categorise all of them as _concepts_, no matter whether the children are
        # categorised as concepts, names, or locations.
        if self.node_type == "concepts":
            for item in self._stream_all_has_parent_edges():
                wikidata_id = item["parent_id"]
                if wikidata_id not in seen:
                    seen.add(wikidata_id)
                    yield wikidata_id

    def _stream_all_same_as_edges(self) -> Generator[dict]:
        """
        Stream raw 'SAME_AS' edges, mapping Wikidata ids to ids from the selected linked ontology.

        Edges are extracted via the following steps:
            1. Run a SPARQL query which retrieves _all_ Wikidata items referencing an id from the linked ontology.
            2. Split the returned ids into chunks. For each chunk, run a second SPARQL query to retrieve a mapping
            between Wikidata ids and ids from the linked ontology. (It is possible to modify the query in step 1 to
            return all the mappings at once, but this makes the query unreliable - sometimes it times out or returns
            invalid JSON. Getting the mappings in chunks is much slower, but it works every time.)
        """
        all_linked_ids = self._get_all_ids()
        for raw_mapping in self._parallelise_requests(
            iter(all_linked_ids), self._get_linked_id_mappings
        ):
            yield {
                "wikidata_id": extract_wikidata_id(raw_mapping),
                "linked_id": raw_mapping["linkedId"]["value"],
                "type": "SAME_AS",
            }

    def _stream_all_has_parent_edges(self) -> Generator[dict]:
        """
        Stream raw 'HAS_PARENT' Wikidata edges, mapping child items to parent items.
        """
        all_linked_ids = self._get_all_ids()
        for raw_mapping in self._parallelise_requests(
            iter(all_linked_ids), self._get_parent_id_mappings
        ):
            parent_id = extract_wikidata_id(raw_mapping)
            child_id = extract_wikidata_id(raw_mapping, "child")

            if parent_id is not None and child_id is not None:
                yield {
                    "child_id": child_id,
                    "parent_id": parent_id,
                    "type": "HAS_PARENT",
                }

    def _stream_raw_edges(self) -> Generator[dict]:
        """
        Stream SAME_AS edges followed by HAS_PARENT edges for the selected `linked_ontology` and `node_type`.
        """
        print("Streaming SAME_AS edges...")
        streamed_wikidata_ids = set()
        for edge in self._stream_all_same_as_edges():
            # Filter for mappings which are part of the selected `node_type`, as determined by the linked ontology.
            # For example, if we are streaming Wikidata 'names' edges linked to LoC ids but the LoC id linked to some
            # Wikidata id is classified as a 'location', we skip it. This filtering process also removes mappings which
            # include invalid LoC ids (of which there are several thousand).
            if self.id_type_checker.id_included_in_selected_type(edge["linked_id"]):
                streamed_wikidata_ids.add(edge["wikidata_id"])
                yield edge

        print("Streaming HAS_PARENT edges...")
        for edge in self._stream_all_has_parent_edges():
            # Only include an edge if its `child_id` was already streamed in a SAME_AS edge, indicating that
            # the child item belongs under the selected `node_type`.
            if edge["child_id"] in streamed_wikidata_ids:
                yield edge

    def _stream_raw_nodes(self) -> Generator[dict]:
        """
        Extract nodes via the following steps:
            1. Stream raw edges and extract Wikidata ids from them.
            2. Split the extracted ids into chunks. For each chunk, run a SPARQL query to retrieve all the corresponding
            Wikidata fields required to create a node.
        """
        all_ids = self._stream_filtered_wikidata_ids()
        yield from self._parallelise_requests(all_ids, self._get_wikidata_items)

    def stream_raw(self) -> Generator[dict]:
        if self.entity_type == "nodes":
            return self._stream_raw_nodes()
        elif self.entity_type == "edges":
            return self._stream_raw_edges()
        else:
            raise ValueError(f"Invalid entity type: {self.entity_type}")
