from collections.abc import Generator

from sources.base_source import BaseSource
from .sparql_client import WikidataSparqlClient
from .sparql_query_builder import SparqlQueryBuilder, NodeType, OntologyType

import smart_open
import boto3
import os

WIKIDATA_ID_PREFIX = "http://www.wikidata.org/entity/"
S3_BULK_LOAD_BUCKET_NAME = os.environ["S3_BULK_LOAD_BUCKET_NAME"]


def extract_wikidata_id(item: dict):
    return item["item"]["value"].removeprefix(WIKIDATA_ID_PREFIX)


class WikidataEdgesSource(BaseSource):
    """
    A source streaming selected Wikidata edges based on the selected linked ontology (LoC or MeSH)
    and node type (concepts, locations, or names). For more information, see the `WikidataLinkedOntologySource` class.
    """

    def __init__(self, node_type: NodeType, linked_ontology: OntologyType):
        self.client = WikidataSparqlClient()
        self.node_type = node_type
        self.linked_ontology = linked_ontology

    @staticmethod
    def _get_linked_ontology_ids(node_type: NodeType, linked_ontology: OntologyType):
        linked_nodes_file_name = f"{linked_ontology}_{node_type}__nodes.csv"
        s3_url = f"s3://{S3_BULK_LOAD_BUCKET_NAME}/{linked_nodes_file_name}"

        ids = set()
        transport_params = {"client": boto3.client("s3")}
        with smart_open.open(s3_url, "r", transport_params=transport_params) as f:
            for line in f:
                ids.add(line.split(",")[0])

        return ids

    def _get_linked_ontology_id_mapping(self) -> list[dict]:
        """
        Return a list of _all_ Wikidata items referencing an id from another ontology (LoC or MeSH). Each returned
        item is a dictionary containing a Wikidata id and the referenced id.
        """

        # Get all Wikidata items referencing an id from the selected linked ontology
        ids_query = SparqlQueryBuilder.get_all_ids_query(self.linked_ontology)
        items = self.client.run_query(ids_query)

        if self.node_type in ["concepts", "locations"]:
            loc_ids = self._get_linked_ontology_ids(
                self.node_type, self.linked_ontology
            )
            filtered_items = [i for i in items if i["linkedId"]["value"] in loc_ids]
        else:
            loc_ids = self._get_linked_ontology_ids(
                "concepts", self.linked_ontology
            ) | self._get_linked_ontology_ids("locations", self.linked_ontology)
            filtered_items = [i for i in items if i["linkedId"]["value"] not in loc_ids]

        ids = []
        for item in filtered_items:
            linked_id = item["linkedId"]["value"]
            wikidata_id = extract_wikidata_id(item)
            ids.append({"wikidata_id": wikidata_id, "linked_id": linked_id})

        print(
            f"Found {len(ids)} Wikidata items referencing a {self.linked_ontology} id of node type {self.node_type}."
        )
        return list(ids)

    def stream_raw(self) -> Generator[dict]:
        ids = self._get_linked_ontology_id_mapping()
        for item in ids:
            yield item
