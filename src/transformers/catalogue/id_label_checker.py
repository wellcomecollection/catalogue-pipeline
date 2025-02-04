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
    def from_source(cls, node_type: NodeType, source: OntologyType) -> dict:
        """Fetch source node data from s3 bulk upload files and create ID-label mapping."""
        id_label_dict = {}

        for row in fetch_from_s3(node_type, source):
            # Extract source id and label at position 0 and 3, respectively
            id_label_dict[row[0]] = row[3]

        print(f"({len(id_label_dict)} ids and labels retrieved.)")

        return cls(**id_label_dict)
