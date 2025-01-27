from collections.abc import Generator, Iterable
from typing import Callable

from sources.base_source import BaseSource
from transformers.base_transformer import EntityType
from utils.streaming import process_stream_in_parallel

from .linked_ontology_id_type_checker import LinkedOntologyIdTypeChecker
from .sparql_client import MAX_PARALLEL_SPARQL_QUERIES, WikidataSparqlClient
from .sparql_query_builder import NodeType, OntologyType, SparqlQueryBuilder

SPARQL_ITEMS_CHUNK_SIZE = 400

WIKIDATA_ID_PREFIX = "http://www.wikidata.org/entity/"


def extract_wikidata_id(item: dict, key: str = "item") -> str:
    """
    Accepts a raw `item` dictionary returned by the Wikidata SPARQL endpoint and returns the Wikidata id of the item.
    """
    assert isinstance(item[key]["value"], str)
    assert item[key]["type"] == "uri"
    return str(item[key]["value"].removeprefix(WIKIDATA_ID_PREFIX))


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

    def _get_all_ids(self) -> list[str]:
        """Return all Wikidata ids corresponding to Wikidata items referencing the selected linked ontology."""
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

        print(f"({len(all_ids)} ids retrieved.)")
        return list(all_ids)

    def _get_linked_id_mappings(self, wikidata_ids: list[str]) -> list[dict]:
        query = SparqlQueryBuilder.get_linked_ids_query(
            wikidata_ids, self.linked_ontology
        )
        return self.client.run_query(query)

    def _get_wikidata_items(self, wikidata_ids: list[str]) -> list:
        query = SparqlQueryBuilder.get_items_query(wikidata_ids, self.node_type)
        return self.client.run_query(query)

    def _get_parent_id_mappings(self, child_wikidata_ids: list[str]) -> list[dict]:
        subclass_of_query = SparqlQueryBuilder.get_parents_query(
            child_wikidata_ids, "subclass_of"
        )
        subclass_of_results = self.client.run_query(subclass_of_query)

        instance_of_query = SparqlQueryBuilder.get_parents_query(
            child_wikidata_ids, "instance_of"
        )
        instance_of_results = self.client.run_query(instance_of_query)

        return subclass_of_results + instance_of_results

    @staticmethod
    def _parallelise_requests(
        items: Iterable, run_sparql_query: Callable[[list], list]
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

    def _stream_wikidata_ids(self) -> Generator[str]:
        """Streams filtered edges using the `_stream_raw_edges` method and extracts Wikidata ids from them."""
        seen = set()
        for item in self._stream_raw_edges():
            wikidata_id: str
            if item["type"] == "SAME_AS":
                wikidata_id = item["wikidata_id"]
            elif item["type"] == "HAS_PARENT":
                wikidata_id = item["parent_id"]
            else:
                raise ValueError(f"Unknown raw edge type {item['type']}.")

            if wikidata_id not in seen:
                seen.add(wikidata_id)
                yield wikidata_id

    def _stream_raw_edges(self) -> Generator[dict]:
        """
        Extract edges via the following steps:
            1. Run a SPARQL query which retrieves _all_ Wikidata items referencing an id from the linked ontology.
            2. Split the returned ids into chunks. For each chunk, run a second SPARQL query to retrieve a mapping
            between Wikidata ids and ids from the linked ontology. (It is possible to modify the query in step 1 to
            return all the mappings at once, but this makes the query unreliable - sometimes it times out or returns
            invalid JSON. Getting the mappings in chunks is much slower, but it works every time.)
            3. Filter the returned id pairs to only include Wikidata ids corresponding to the selected node type
            (i.e. concepts, locations, or names).
        """
        all_linked_ids = self._get_all_ids()

        print("Streaming linked Wikidata ids...")
        for raw_mapping in self._parallelise_requests(
            all_linked_ids, self._get_linked_id_mappings
        ):
            linked_id = raw_mapping["linkedId"]["value"]
            wikidata_id = extract_wikidata_id(raw_mapping)
            mapping = {
                "wikidata_id": wikidata_id,
                "linked_id": linked_id,
                "type": "SAME_AS",
            }

            # Only yield the mapping if the linked id corresponds to the selected `node_type`, as determined by the
            # linked ontology. For example, if we want to stream Wikidata 'names' edges, but we classify the referenced
            # LoC id is a 'locations' id, we skip it.
            # This also removes mappings which include invalid LoC ids (of which there are several thousand).
            if self.id_type_checker.id_included_in_selected_type(mapping["linked_id"]):
                yield mapping

        print("Streaming parent Wikidata ids...")
        for raw_mapping in self._parallelise_requests(
            all_linked_ids, self._get_parent_id_mappings
        ):
            parent_id = extract_wikidata_id(raw_mapping)
            mapping = {
                "child_id": extract_wikidata_id(raw_mapping, "child"),
                "parent_id": parent_id,
                "type": "HAS_PARENT",
            }

            yield mapping

    def _stream_raw_nodes(self) -> Generator[dict]:
        """
        Extract nodes via the following steps:
            1. Stream edges via the `_stream_raw_edges` method and extract Wikidata ids from the streamed edges.
            2. Split the extracted ids into chunks. For each chunk, run a SPARQL query to retrieve all the corresponding
            Wikidata fields required to create a node.
        """
        all_ids = self._stream_wikidata_ids()
        yield from self._parallelise_requests(all_ids, self._get_wikidata_items)

    def stream_raw(self) -> Generator[dict]:
        if self.entity_type == "nodes":
            return self._stream_raw_nodes()
        elif self.entity_type == "edges":
            return self._stream_raw_edges()
        else:
            raise ValueError(f"Invalid entity type: {self.entity_type}")
