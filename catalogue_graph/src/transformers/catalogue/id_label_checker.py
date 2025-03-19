from collections import defaultdict
from itertools import product

from models.graph_node import ConceptSource, ConceptType
from utils.aws import NodeType, OntologyType, fetch_transformer_output_from_s3

AGENT_TYPES = ("Person", "Agent", "Organisation")


SOURCES_BY_PRIORITY: list[ConceptSource] = ["nlm-mesh", "lc-subjects", "lc-names"]
AMBIGUITY_THRESHOLD = 1

with open("transformers/catalogue/data/blacklisted_concept_labels.txt") as f:
    BLACKLISTED_CONCEPT_LABELS = [line.strip() for line in f]


def _concept_source_from_id(source_id: str) -> ConceptSource:
    if source_id[0] == "n":
        return "lc-names"
    if source_id[0] == "s":
        return "lc-subjects"
    if source_id[0] == "D":
        return "nlm-mesh"

    raise ValueError(f"Unexpected source id {source_id}")


class IdLabelChecker:
    """
    A bidirectional dictionary for checking catalogue concepts against data from source ontologies.
    """

    ids_to_labels: dict[ConceptSource, dict[str, str]] = defaultdict(
        lambda: defaultdict(str)
    )
    ids_to_alternative_labels: dict[ConceptSource, dict[str, list[str]]] = defaultdict(
        lambda: defaultdict(list)
    )

    labels_to_ids: dict[ConceptSource, dict[str, list[str]]] = defaultdict(
        lambda: defaultdict(list)
    )
    alternative_labels_to_ids: dict[ConceptSource, dict[str, list[str]]] = defaultdict(
        lambda: defaultdict(list)
    )

    def __init__(
        self,
        node_type: NodeType | list[NodeType],
        source: OntologyType | list[OntologyType],
    ):
        if not isinstance(node_type, list):
            node_type = [node_type]
        if not isinstance(source, list):
            source = [source]

        for nt, s in product(node_type, source):
            for row in fetch_transformer_output_from_s3(nt, s):
                source_id = row[":ID"]
                label = row["label:String"].lower()
                alternative_labels = [
                    label.lower()
                    for label in row["alternative_labels:String"].split("||")
                    if label != ""
                ]

                concept_source = _concept_source_from_id(source_id)

                self.ids_to_labels[concept_source][source_id] = label
                self.ids_to_alternative_labels[concept_source][
                    source_id
                ] = alternative_labels

                self.labels_to_ids[concept_source][label].append(source_id)
                for label in alternative_labels:
                    self.alternative_labels_to_ids[concept_source][label].append(
                        source_id
                    )

    def get_id(self, label: str, concept_type: ConceptType) -> str | None:
        """
        Given some label, return exactly one closest-matching source concept id (or 'None' if no match found).
        """
        # Do not attempt to match blacklisted concept labels.
        if label in BLACKLISTED_CONCEPT_LABELS:
            return None

        # First, try to match the concept label to a 'main' source concept label, in order of priority.
        for source in SOURCES_BY_PRIORITY:
            if len(source_ids := self.labels_to_ids[source][label]) > 0:
                return source_ids[0]

        # If no matches found, try matching on alternative labels
        for source in SOURCES_BY_PRIORITY:
            if len(source_ids := self.alternative_labels_to_ids[source][label]) > 0:
                # If a label matches more the alternative labels of more than 'AMBIGUITY_THRESHOLD' concepts
                # from any given source ontology, it's too ambiguous, and we shouldn't match it.
                if len(source_ids) > AMBIGUITY_THRESHOLD:
                    return None

                # Try not to match people/organisations to things
                if concept_type in AGENT_TYPES and source in (
                    "nlm-mesh",
                    "lc-subjects",
                ):
                    continue

                # Try not to match things to people/organisations
                if (
                    concept_type not in AGENT_TYPES
                    and source == "lc-names"
                ):
                    continue

                return source_ids[0]

        return None

    def get_label(self, source_id: str, source: ConceptSource) -> str | None:
        """Given a source id from a specific source (e.g. nlm-mesh, lc-subjects), return its label."""
        return self.ids_to_labels[source][source_id]

    def get_alternative_labels(
        self, source_id: str, source: ConceptSource
    ) -> list[str]:
        """Given a source id from a specific source (e.g. nlm-mesh, lc-subjects), return its alternative labels."""
        return self.ids_to_alternative_labels[source][source_id]
