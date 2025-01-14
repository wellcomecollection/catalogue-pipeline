from collections.abc import Generator

from sources.base_source import BaseSource
from .sparql_client import WikidataSparqlClient
from .sparql_query_builder import SparqlQueryBuilder, NodeType, OntologyType

import concurrent.futures
from itertools import islice

from .edges_source import WikidataEdgesSource
from utils.streaming import generator_to_chunks


SPARQL_ITEMS_CHUNK_SIZE = 300


class WikidataLinkedOntologySource(BaseSource):
    """
    A source streaming selected Wikidata nodes or edges based on the selected linked ontology (LoC or MeSH)
    and node type (concepts, locations, or names). For example, if a combination of "LoC" and "locations" is selected,
    only Wikidata items referencing LoC geographic nodes are streamed.

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

    def __init__(self, node_type: NodeType, linked_ontology: OntologyType, entity_type):
        self.client = WikidataSparqlClient()
        self.node_type = node_type
        self.linked_ontology = linked_ontology
        self.entity_type = entity_type
        self.edges_source = WikidataEdgesSource(node_type, linked_ontology)

    def _stream_wikidata_ids(self) -> Generator[dict]:
        seen = set()
        for item in self.edges_source.stream_raw():
            wikidata_id = item["wikidata_id"]
            if wikidata_id in seen:
                yield
            else:
                seen.add(wikidata_id)
                yield wikidata_id

    def _stream_raw_nodes(self) -> Generator[dict]:
        all_ids = self._stream_wikidata_ids()
        chunks = generator_to_chunks(all_ids, SPARQL_ITEMS_CHUNK_SIZE)

        def run_query(chunk) -> list:
            query = SparqlQueryBuilder.get_items_query(chunk, self.node_type)
            return self.client.run_query(query)

        with concurrent.futures.ThreadPoolExecutor() as executor:
            # Run the first 3 queries in parallel
            futures = {executor.submit(run_query, chunk) for chunk in islice(chunks, 3)}

            while futures:
                # Wait for one or more queries to complete
                done, futures = concurrent.futures.wait(
                    futures, return_when=concurrent.futures.FIRST_COMPLETED
                )

                # Top up with new queries to keep the total number of parallel queries at 3
                for chunk in islice(chunks, len(done)):
                    futures.add(executor.submit(run_query, chunk))

                for future in done:
                    items = future.result()
                    for item in items:
                        yield item

    def stream_raw(self) -> Generator[dict]:
        if self.entity_type == "edges":
            return self.edges_source.stream_raw()

        return self._stream_raw_nodes()
