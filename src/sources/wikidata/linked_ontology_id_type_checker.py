from functools import lru_cache

import boto3
import smart_open

import config
from .sparql_query_builder import NodeType, OntologyType


class LinkedOntologyIdTypeChecker:
    """
    A class for checking whether ids from a given linked ontology (LoC or MeSH) are classified under
    a selected node type (concepts, locations, or names).
    """

    def __init__(self, node_type: NodeType, linked_ontology: OntologyType):
        self.node_type = node_type
        self.linked_ontology = linked_ontology

        # MeSH only has concepts and locations, so make sure we don't attempt to extract names.
        if node_type == "names":
            assert (
                linked_ontology != "mesh"
            ), "Invalid node_type for ontology type MeSH."

    @lru_cache
    def _get_linked_ontology_ids(self, node_type: NodeType) -> set[str]:
        """Return all ids classified under a given `node_type` for the selected ontology."""
        # Retrieve the bulk load file outputted by the relevant transformer so that we can extract ids from it.
        linked_nodes_file_name = f"{self.linked_ontology}_{node_type}__nodes.csv"
        s3_url = f"s3://{config.S3_BULK_LOAD_BUCKET_NAME}/{linked_nodes_file_name}"

        print(
            f"Retrieving ids of type '{node_type}' from ontology '{self.linked_ontology}' from S3.",
            end=" ",
            flush=True,
        )

        ids = set()

        transport_params = {"client": boto3.client("s3")}
        with smart_open.open(s3_url, "r", transport_params=transport_params) as f:
            # Loop through all items in the file and extract the id from each item
            for i, line in enumerate(f):
                # Skip header
                if i == 0:
                    continue
                ids.add(line.split(",")[0])

        print(f"({len(ids)} ids retrieved.)")

        return ids

    def id_included_in_selected_type(self, linked_id: str) -> bool:
        """
        Return `True` if a given linked ontology id is classified under the selected node type (concepts,
        locations, or names).
        """
        return linked_id in self._get_linked_ontology_ids(self.node_type)

    def id_is_valid(self, linked_id: str) -> bool:
        """Returns 'True' if the given id from the selected linked ontology is valid."""
        is_valid = False
        is_valid |= linked_id in self._get_linked_ontology_ids("concepts")
        is_valid |= linked_id in self._get_linked_ontology_ids("locations")

        if self.linked_ontology == "loc":
            is_valid |= linked_id in self._get_linked_ontology_ids("names")

        return is_valid
