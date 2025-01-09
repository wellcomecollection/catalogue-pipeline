from collections.abc import Generator

from sources.base_source import BaseSource
from .sparql_client import WikidataSparqlClient
from .sparql_query_builder import SparqlQueryBuilder, NodeType, LinkedSource

WIKIDATA_ID_PREFIX = "http://www.wikidata.org/entity/"


class WikidataConceptsSource(BaseSource):
    def __init__(self, node_type: NodeType, linked_source: LinkedSource):
        self.client = WikidataSparqlClient()
        self.node_type = node_type
        self.linked_source = linked_source

    def _get_all_wikidata_ids(self) -> list[str]:
        """Returns the IDs of all Wikidata items which reference a Library of Congress ID.
        There are currently about 1.6 million such items, and the query takes ~1 minute to run.
        """

        loc_ids_query = """
            SELECT DISTINCT ?item WHERE {      
              ?item p:P244 ?statement0.
              ?statement0 ps:P244 _:anyValueP244.
            }
        """

        items = self.client.run_query(loc_ids_query)

        raw_ids: list[str] = [item["item"]["value"] for item in items]
        ids = [raw_id.removeprefix(WIKIDATA_ID_PREFIX) for raw_id in raw_ids]

        return ids

    def stream_raw(self) -> Generator[dict]:
        all_ids = self._get_all_wikidata_ids()

        chunk_size = 300

        for i in range(0, len(all_ids), chunk_size):
            chunk = all_ids[i : i + chunk_size]
            query = SparqlQueryBuilder.get_items_query(
                chunk, self.node_type, self.linked_source
            )
            items = self.client.run_query(query)

            for item in items:
                yield item
