import os
from functools import lru_cache

import boto3
import smart_open


from .sparql_query_builder import NodeType, OntologyType


S3_BULK_LOAD_BUCKET_NAME = os.environ["S3_BULK_LOAD_BUCKET_NAME"]


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
        s3_url = f"s3://{S3_BULK_LOAD_BUCKET_NAME}/{linked_nodes_file_name}"

        print(f"Retrieving {linked_nodes_file_name} from S3.")

        ids = set()
        transport_params = {"client": boto3.client("s3")}
        with smart_open.open(s3_url, "r", transport_params=transport_params) as f:
            # Loop through all items in the file and extract the id from each item
            for line in f:
                ids.add(line.split(",")[0])

        print(
            f"Retrieved {len(ids)} ids of type '{node_type}' from ontology '{self.linked_ontology}'."
        )

        return ids

    def id_included_in_selected_type(self, linked_id: str) -> bool:
        """Return `True` if a given linked ontology id is classified under the selected node type (concepts,
        locations, or names)."""

        # To check whether a Library of Congress id is classified under 'names', we could examine all the 'names' ids,
        # but the corresponding file is large and it would take too long. Instead, it's better to check that the
        # LoC id starts  with an 'n' and that it is not classified under 'locations'.
        if self.linked_ontology == "loc" and self.node_type == "names":
            location_ids = self._get_linked_ontology_ids("locations")
            return linked_id not in location_ids and linked_id[0] == "n"

        return linked_id in self._get_linked_ontology_ids(self.node_type)
