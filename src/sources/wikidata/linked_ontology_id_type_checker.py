from utils.aws import NodeType, OntologyType, fetch_from_s3


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
            assert linked_ontology != "mesh", (
                "Invalid node_type for ontology type MeSH."
            )

    def _get_linked_ontology_ids(self, node_type: NodeType) -> set[str]:
        """Return all ids classified under a given `node_type` for the selected ontology."""
        # Retrieve the bulk load file outputted by the relevant transformer so that we can extract ids from it.
        ids = set()
        for row in fetch_from_s3(node_type, self.linked_ontology):
            ids.add(row[0])

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
