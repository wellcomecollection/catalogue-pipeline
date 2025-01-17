from collections.abc import Generator

from sources.base_source import BaseSource
from transformers.base_transformer import EntityType
from utils.streaming import process_stream_in_parallel

from .linked_ontology_id_type_checker import LinkedOntologyIdTypeChecker
from .sparql_client import MAX_PARALLEL_SPARQL_QUERIES, WikidataSparqlClient
from .sparql_query_builder import NodeType, OntologyType, SparqlQueryBuilder

SPARQL_ITEMS_CHUNK_SIZE = 400

WIKIDATA_ID_PREFIX = "http://www.wikidata.org/entity/"


def extract_wikidata_id(item: dict) -> str:
    """
    Accepts a raw `item` dictionary returned by the Wikidata SPARQL endpoint and returns the Wikidata id of the item.
    """
    assert isinstance(item["item"]["value"], str)
    assert item["item"]["type"] == "uri"
    return item["item"]["value"].removeprefix(WIKIDATA_ID_PREFIX)


class WikidataLinkedOntologySource(BaseSource):
    """
    A source for streaming selected Wikidata nodes/edges. There are _many_ Wikidata items, so we cannot store all of
    them in the graph. Instead, we only include items which reference an id from a selected linked ontology,
    (LoC or MeSH), as defined by the `linked_ontology` parameter.

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

    def _get_all_ids(self) -> Generator[str]:
        """Return all Wikidata ids corresponding to Wikidata items referencing the selected linked ontology."""
        print(f"Retrieving Wikidata ids linked to {self.linked_ontology} items.")
        ids_query = SparqlQueryBuilder.get_all_ids_query(self.linked_ontology)
        id_items = self.client.run_query(ids_query)

        # Deduplicate. (We could deduplicate as part of the SPARQL query via the 'DISTINCT' keyword,
        # but that would make the query significantly slower. It's faster to deduplicate here.)
        all_ids = set(extract_wikidata_id(item) for item in id_items)

        print(f"Retrieved a total of {len(all_ids)} Wikidata ids.")
        yield from all_ids

    def _get_linked_id_mappings(self, wikidata_ids: list[str]) -> list[dict]:
        query = SparqlQueryBuilder.get_linked_ids_query(
            wikidata_ids, self.linked_ontology
        )
        return self.client.run_query(query)

    def _get_linked_items(self, wikidata_ids: list[str]) -> list:
        query = SparqlQueryBuilder.get_items_query(wikidata_ids, self.node_type)
        return self.client.run_query(query)

    def _stream_wikidata_ids(self) -> Generator[str]:
        """Streams filtered edges using the `_stream_raw_edges` method and extracts Wikidata ids from them."""
        seen = set()
        for item in self._stream_raw_edges():
            wikidata_id: str = item["wikidata_id"]
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
        all_ids = self._get_all_ids()

        # Parallelise the second query to retrieve the mappings faster.
        for raw_mapping in process_stream_in_parallel(
            all_ids,
            self._get_linked_id_mappings,
            SPARQL_ITEMS_CHUNK_SIZE,
            MAX_PARALLEL_SPARQL_QUERIES,
        ):
            linked_id = raw_mapping["linkedId"]["value"]
            wikidata_id = extract_wikidata_id(raw_mapping)
            mapping = {"wikidata_id": wikidata_id, "linked_id": linked_id}

            # Only yield the mapping if the linked id corresponds to the selected `node_type`, as determined by the
            # linked ontology. For example, if we want to stream Wikidata 'names' edges, but we classify the referenced
            # LoC id is a 'locations' id, we skip it.
            if self.id_type_checker.id_included_in_selected_type(mapping["linked_id"]):
                yield mapping

    def _stream_raw_nodes(self) -> Generator[dict]:
        """
        Extract nodes via the following steps:
            1. Stream edges via the `_stream_raw_edges` method and extract Wikidata ids from the streamed edges.
            2. Split the extracted ids into chunks. For each chunk, run a SPARQL query to retrieve all the corresponding
            Wikidata fields required to create a node.
        """
        all_ids = self._stream_wikidata_ids()

        yield from process_stream_in_parallel(
            all_ids,
            self._get_linked_items,
            SPARQL_ITEMS_CHUNK_SIZE,
            MAX_PARALLEL_SPARQL_QUERIES,
        )

    def stream_raw(self) -> Generator[dict]:
        if self.entity_type == "nodes":
            return self._stream_raw_nodes()
        elif self.entity_type == "edges":
            return self._stream_raw_edges()
        else:
            raise ValueError(f"Invalid entity type: {self.entity_type}")
