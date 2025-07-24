from collections.abc import Callable, Generator, Iterator
from functools import lru_cache

from sources.base_source import BaseSource
from transformers.base_transformer import EntityType
from utils.ontology_id_checker import is_id_classified_as_node_type, is_id_in_ontology
from utils.streaming import process_stream_in_parallel
from utils.types import NodeType, OntologyType

from .sparql_client import SPARQL_MAX_PARALLEL_QUERIES, WikidataSparqlClient
from .sparql_query_builder import SparqlQueryBuilder, WikidataEdgeQueryType

SPARQL_ITEMS_CHUNK_SIZE = 400

WIKIDATA_ID_PREFIX = "http://www.wikidata.org/entity/"

PEOPLE_RELATIONSHIP_TYPES: list[WikidataEdgeQueryType] = [
    "has_father",
    "has_mother",
    "has_sibling",
    "has_spouse",
    "has_child",
]


def _parallelise_sparql_requests(
    items: Iterator, run_sparql_query: Callable[[list], list]
) -> Generator:
    """Accept an `items` generator and a `run_sparql_query` method. Split `items` into chunks and apply
    `run_sparql_query` to each chunk. Return a single generator of results."""
    yield from process_stream_in_parallel(
        items,
        run_sparql_query,
        SPARQL_ITEMS_CHUNK_SIZE,
        SPARQL_MAX_PARALLEL_QUERIES,
    )


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

    def _get_wikidata_items(self, wikidata_ids: list[str]) -> list:
        query = SparqlQueryBuilder.get_items_query(wikidata_ids, self.node_type)
        return self.client.run_query(query)

    def _stream_all_edges_by_type(
        self, edge_type: WikidataEdgeQueryType
    ) -> Generator[dict]:
        """
        Given an `edge_type`, return a generator of all edges starting from all Wikidata items linking to the selected
        ontology.

        Edges are extracted via the following steps:
            1. Run a SPARQL query which retrieves _all_ Wikidata items referencing an id from the linked ontology.
            2. Split the returned ids into chunks. For each chunk, run a second SPARQL query to retrieve the requested
            edges for all ids in the chunk. (It is possible to modify the query in step 1 to return the edges directly,
            but this makes the query unreliable - sometimes it times out or returns invalid JSON. Getting the edges
            in chunks is much slower, but it works every time.)
        """

        def get_edges(wikidata_ids: list[str]) -> list[dict]:
            query = SparqlQueryBuilder.get_edge_query(wikidata_ids, edge_type)
            return self.client.run_query(query)

        all_ids = self._get_all_ids()
        for raw_mapping in _parallelise_sparql_requests(iter(all_ids), get_edges):
            from_id = extract_wikidata_id(raw_mapping, "fromItem")

            # The 'toItem' ids of SAME_AS edges are MeSH/LoC ids, so we take the raw value instead of extracting
            # the ids via the `extract_wikidata_id` function
            if edge_type in ("same_as_mesh", "same_as_loc"):
                to_id = raw_mapping["toItem"]["value"]
            else:
                to_id = extract_wikidata_id(raw_mapping, "toItem")

            if from_id is not None and to_id is not None:
                yield {"from_id": from_id, "to_id": to_id}

    def _stream_all_same_as_edges(self) -> Generator[dict]:
        if self.linked_ontology == "loc":
            yield from self._stream_all_edges_by_type("same_as_loc")
        elif self.linked_ontology == "mesh":
            yield from self._stream_all_edges_by_type("same_as_mesh")

    def _stream_all_has_parent_edges(self) -> Generator[dict]:
        yield from self._stream_all_edges_by_type("instance_of")
        yield from self._stream_all_edges_by_type("subclass_of")

    def _stream_filtered_wikidata_ids(self) -> Generator[str]:
        """Streams all wikidata ids to be processed as nodes given the selected `node_type`."""
        seen = set()

        # Stream all SAME_AS edges and extract Wikidata ids from them, making sure to deduplicate
        # (a given Wikidata id can appear in more than one edge).
        for edge in self._stream_all_same_as_edges():
            wikidata_id, linked_id = edge["from_id"], edge["to_id"]
            linked_id_is_valid = is_id_in_ontology(linked_id, self.linked_ontology)
            if linked_id_is_valid and wikidata_id not in seen:
                # Add Wikidata id to `seen` no matter if it's part of the selected node type
                # to make sure it is not processed again as a parent below.
                seen.add(wikidata_id)

                if is_id_classified_as_node_type(
                    linked_id, self.linked_ontology, self.node_type
                ):
                    yield wikidata_id

        # Stream HAS_PARENT edges and extract Wikidata ids of all parents (children are streamed above). Filter out
        # all parent ids which reference a linked ontology ids. All remaining ids belong to items which do not
        # reference a MeSH/LoC id. We categorise all of them as _concepts_, no matter whether the children are
        # categorised as concepts, names, or locations.
        if self.node_type == "concepts":
            for edge in self._stream_all_has_parent_edges():
                parent_wikidata_id = edge["to_id"]
                if parent_wikidata_id not in seen:
                    seen.add(parent_wikidata_id)
                    yield parent_wikidata_id

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
            if is_id_classified_as_node_type(
                edge["to_id"], self.linked_ontology, self.node_type
            ):
                streamed_wikidata_ids.add(edge["from_id"])
                yield {**edge, "type": "SAME_AS"}

        print("Streaming HAS_PARENT edges...")
        for edge in self._stream_all_has_parent_edges():
            # Only include an edge if its `from_id` was already streamed in a SAME_AS edge, indicating that
            # the child item belongs under the selected `node_type`.
            if edge["from_id"] in streamed_wikidata_ids:
                streamed_wikidata_ids.add(edge["to_id"])
                yield {**edge, "type": "HAS_PARENT"}

        # The following edges only apply to people (SourceName) nodes
        if self.node_type == "names":
            print("Streaming HAS_FIELD_OF_WORK edges...")
            for edge in self._stream_all_edges_by_type("has_field_of_work"):
                # Only include an edge if its `to_id` has a corresponding concept node in the graph
                if edge["from_id"] in streamed_wikidata_ids and is_id_in_ontology(
                    edge["to_id"], "wikidata"
                ):
                    yield {**edge, "type": "HAS_FIELD_OF_WORK"}

            print("Streaming RELATED_TO edges...")
            for relationship_type in PEOPLE_RELATIONSHIP_TYPES:
                for edge in self._stream_all_edges_by_type(relationship_type):
                    if (
                        edge["from_id"] in streamed_wikidata_ids
                        and edge["to_id"] in streamed_wikidata_ids
                    ):
                        yield {
                            **edge,
                            "type": "RELATED_TO",
                            "subtype": relationship_type,
                        }

    def _stream_raw_nodes(self) -> Generator[dict]:
        """
        Extract nodes via the following steps:
            1. Stream raw edges and extract Wikidata ids from them.
            2. Split the extracted ids into chunks. For each chunk, run a SPARQL query to retrieve all the corresponding
            Wikidata fields required to create a node.
        """
        all_ids = self._stream_filtered_wikidata_ids()
        yield from _parallelise_sparql_requests(all_ids, self._get_wikidata_items)

    def stream_raw(self) -> Generator[dict]:
        if self.entity_type == "nodes":
            return self._stream_raw_nodes()
        elif self.entity_type == "edges":
            return self._stream_raw_edges()
        else:
            raise ValueError(f"Invalid entity type: {self.entity_type}")
