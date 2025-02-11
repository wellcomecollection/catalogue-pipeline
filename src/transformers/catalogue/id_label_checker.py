from itertools import product
from typing import Any

from utils.aws import NodeType, OntologyType, fetch_from_s3


class IdLabelChecker(dict):
    """
    A bidirectional dictionary for checking catalogue concepts against data from source ontologies.
    """

    def __init__(self, *args: Any, **kwargs: Any):
        super().__init__(*args, **kwargs)
        self.inverse: dict = {}
        for key, value in self.items():
            self.inverse.setdefault(value, []).append(key)

    @classmethod
    def from_source(
        cls,
        node_type: NodeType | list[NodeType],
        source: OntologyType | list[OntologyType],
    ) -> dict:
        """Fetch source node data from s3 bulk upload files and create ID-label mapping."""
        id_label_dict = {}

        if not isinstance(node_type, list):
            node_type = [node_type]

        if not isinstance(source, list):
            source = [source]

        for nt, s in product(node_type, source):
            for row in fetch_from_s3(nt, s):
                # Extract source id and label at position 0 and 3, respectively
                id_label_dict[row[":ID"]] = row["label:String"].lower()

        print(f"({len(id_label_dict)} ids and labels retrieved.)")

        return cls(**id_label_dict)
