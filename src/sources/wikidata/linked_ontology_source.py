import os
from collections.abc import Generator
from functools import lru_cache

import boto3
import smart_open

from sources.base_source import BaseSource
from transformers.base_transformer import EntityType

from utils.streaming import process_stream_in_parallel

from .sparql_client import WikidataSparqlClient
from .sparql_query_builder import NodeType, OntologyType, SparqlQueryBuilder

SPARQL_ITEMS_CHUNK_SIZE = 400
MAX_PARALLEL_SPARQL_QUERIES = 3

S3_BULK_LOAD_BUCKET_NAME = os.environ["S3_BULK_LOAD_BUCKET_NAME"]
WIKIDATA_ID_PREFIX = "http://www.wikidata.org/entity/"


def extract_wikidata_id(item: dict) -> str:
    assert isinstance(item["item"]["value"], str)
    return item["item"]["value"].removeprefix(WIKIDATA_ID_PREFIX)


class WikidataLinkedOntologySource(BaseSource):
    """
    A source for streaming selected Wikidata nodes/edges. There are _many_ Wikidata items, so we cannot store all of
    them in the graph. Instead, we only include items which reference an id from a selected linked ontology,
    (LoC or MeSH), as defined by the `linked_ontology` parameter.

    Wikidata puts strict limits on the resources which can be consumed by a single query, and queries which include
    filters or do other expensive processing often time out or return a stack overflow error. This means we need
    to use a somewhat convoluted way for extracting the Wikidata nodes/edges we need.

    To extract nodes:
        1. Run a SPARQL query which retrieves _all_ Wikidata ids referencing an id from the selected linked ontology.
        (WikidataEdgesSource is utilised to run the query.)
        2. Split the returned ids into chunks and run a SPARQL query for each chunk. The query retrieves all the node
        properties we are interested in for each id in the chunk.
        3. Stream the returned items as usual.

    To extract edges (via the `WikidataEdgesSource` class):
        1. Run a SPARQL query which retrieves _all_ Wikidata items referencing an id from the selected linked ontology,
        and returns mappings between Wikidata ids and ids from the linked ontology.
        2. Filter the returned id pairs to only include Wikidata ids corresponding to the selected node type
        (i.e. concepts, locations, or names).
        3. Stream the filtered items as usual.
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
    def _get_linked_ontology_ids(self, node_type: NodeType) -> set[str]:
        linked_nodes_file_name = f"{self.linked_ontology}_{node_type}__nodes.csv"
        s3_url = f"s3://{S3_BULK_LOAD_BUCKET_NAME}/{linked_nodes_file_name}"

        ids = set()
        transport_params = {"client": boto3.client("s3")}
        with smart_open.open(s3_url, "r", transport_params=transport_params) as f:
            for line in f:
                ids.add(line.split(",")[0])

        return ids

    def _linked_id_exists_in_selected_node_type(self, linked_id: str) -> bool:
        if self.linked_ontology == "mesh":
            return True
        elif self.linked_ontology == "loc":
            if self.node_type in ["concepts", "locations"]:
                return linked_id in self._get_linked_ontology_ids(self.node_type)
            elif self.node_type == "names":
                location_ids = self._get_linked_ontology_ids("locations")
                return linked_id not in location_ids and linked_id[0] == "n"
            else:
                raise ValueError(f"Invalid node type: {self.linked_ontology}")
        else:
            raise ValueError(f"Invalid linked ontology {self.linked_ontology}")

    def _stream_raw_edges(self) -> Generator[dict]:
        # First, get the ids of _all_ Wikidata items which reference an id from the selected linked ontology
        ids_query = SparqlQueryBuilder.get_all_ids_query(self.linked_ontology)
        id_items = self.client.run_query(ids_query)

        # Deduplicate. (We could deduplicate as part of the SPARQL query via the 'DISTINCT' keyword,
        # but that would make the query significantly slower. It's faster to deduplicate here.)
        all_ids = iter(set(extract_wikidata_id(item) for item in id_items))

        def get_linked_ids(ids_chunk: list[str]) -> list:
            query = SparqlQueryBuilder.get_linked_ids_query(
                ids_chunk, self.linked_ontology
            )
            return self.client.run_query(query)

        # Split ids into chunks. For each chunk, run a separate SPARQL query to retrieve a mapping between Wikidata ids
        # and ids from the linked ontology. (We could run a SPARQL query to get _all_ mappings at once, but this query
        # is not reliable - sometimes it times out or returns invalid JSON. Getting the mappings in chunks is much
        # slower, but it works every time.)
        for raw_mapping in process_stream_in_parallel(
            all_ids,
            get_linked_ids,
            SPARQL_ITEMS_CHUNK_SIZE,
            MAX_PARALLEL_SPARQL_QUERIES,
        ):
            linked_id = raw_mapping["linkedId"]["value"]
            wikidata_id = extract_wikidata_id(raw_mapping)
            mapping = {"wikidata_id": wikidata_id, "linked_id": linked_id}

            # Only yield the mapping if the linked id corresponds to the selected `node_type`, as determined by the
            # linked ontology. For example, if we want to stream Wikidata 'names' edges, but we classify the referenced
            # LoC id is a 'locations' id, we skip it.
            if self._linked_id_exists_in_selected_node_type(mapping["linked_id"]):
                yield mapping

    def _stream_wikidata_ids(self) -> Generator[str]:
        """Streams filtered edges using the `_stream_raw_edges` method and extracts Wikidata ids from them."""
        seen = set()
        for item in self._stream_raw_edges():
            wikidata_id: str = item["wikidata_id"]
            if wikidata_id not in seen:
                seen.add(wikidata_id)
                yield wikidata_id

    def _stream_raw_nodes(self) -> Generator[dict]:
        def get_linked_items(chunk: list[str]) -> list:
            query = SparqlQueryBuilder.get_items_query(chunk, self.node_type)
            return self.client.run_query(query)

        all_ids = self._stream_wikidata_ids()

        yield from process_stream_in_parallel(
            all_ids,
            get_linked_items,
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
