import os
from collections import defaultdict

from models.events import BasePipelineEvent, BulkLoaderEvent
from utils.aws import get_csv_from_s3
from utils.types import ConceptSource, ConceptType, TransformerType

AGENT_TYPES = ("Person", "Agent", "Organisation")
SOURCES_BY_PRIORITY: list[ConceptSource] = [
    "weco-authority",
    "nlm-mesh",
    "lc-subjects",
    "lc-names",
]
AMBIGUITY_THRESHOLD = 1

with open(f"{os.path.dirname(__file__)}/data/concept_label_deny_list.txt") as f:
    CONCEPT_DENY_LIST = [line.strip().lower() for line in f]


def concept_source_from_id(source_id: str) -> ConceptSource:
    if source_id[0] == "n":
        return "lc-names"
    if source_id[0] == "s":
        return "lc-subjects"
    if source_id[0] == "D":
        return "nlm-mesh"

    raise ValueError(f"Unexpected source id {source_id}")


class IdLabelChecker:
    """
    A set of methods for checking catalogue concepts against data from source ontologies.
    """

    def __init__(self, transformers: list[TransformerType], event: BasePipelineEvent):
        # Nested dictionaries mapping source ids to labels/alternative labels and vice versa.
        # The dictionaries are nested to group ids/labels by source ontology.
        self.ids_to_labels: dict[ConceptSource, dict[str, str]] = defaultdict(
            lambda: defaultdict(str)
        )
        self.ids_to_alternative_labels: dict[ConceptSource, dict[str, list[str]]] = (
            defaultdict(lambda: defaultdict(list))
        )
        self.labels_to_ids: dict[ConceptSource, dict[str, list[str]]] = defaultdict(
            lambda: defaultdict(list)
        )
        self.alternative_labels_to_ids: dict[ConceptSource, dict[str, list[str]]] = (
            defaultdict(lambda: defaultdict(list))
        )
        # Records which transformer's bulk load file each source id came from,
        # so that stub nodes can be minted with the matching node label.
        self.ids_to_transformers: dict[str, TransformerType] = {}

        for transformer in transformers:
            event = BulkLoaderEvent(
                transformer_type=transformer,
                entity_type="nodes",
                pipeline_date=event.pipeline_date,
                environment=event.environment,
            )
            for row in get_csv_from_s3(event.get_s3_uri()):
                source_id = row[":ID"]
                # Labels are stored with their original casing (they are reused verbatim when
                # minting stub nodes); lookups lowercase them at comparison time.
                label = row["label:String"]
                alternative_labels = [
                    label.lower()
                    for label in row["alternative_labels:String"].split("||")
                    if label != ""
                ]

                concept_source = concept_source_from_id(source_id)
                self.ids_to_transformers[source_id] = transformer
                self._add_label_mapping(label, source_id, concept_source)
                self._add_alternative_label_mappings(
                    alternative_labels, source_id, concept_source
                )

    def _add_label_mapping(
        self, label: str, source_id: str, concept_source: ConceptSource
    ) -> None:
        self.ids_to_labels[concept_source][source_id] = label
        self.labels_to_ids[concept_source][label.lower()].append(source_id)

    def _add_alternative_label_mappings(
        self, labels: list[str], source_id: str, concept_source: ConceptSource
    ) -> None:
        self.ids_to_alternative_labels[concept_source][source_id] = labels
        for label in labels:
            self.alternative_labels_to_ids[concept_source][label].append(source_id)

    def _normalise_label(self, label: str) -> str:
        return label.lower()

    def get_id(self, label: str, concept_type: ConceptType) -> str | None:
        """
        Given some label, return exactly one closest-matching source concept id (or 'None' if no match found).
        """
        label = self._normalise_label(label)

        # Do not attempt to match blacklisted concept labels.
        if label in CONCEPT_DENY_LIST:
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
                if concept_type not in AGENT_TYPES and source == "lc-names":
                    continue

                return source_ids[0]

        return None

    def get_label(self, source_id: str, source: ConceptSource) -> str | None:
        """Given a source id from a specific source (e.g. nlm-mesh, lc-subjects), return its label."""
        return self.ids_to_labels[source].get(source_id, None)

    def get_transformer(self, source_id: str) -> TransformerType | None:
        """Given a source id, return the transformer type whose bulk load file it came from."""
        return self.ids_to_transformers.get(source_id, None)

    def get_alternative_labels(
        self, source_id: str, source: ConceptSource
    ) -> list[str]:
        """Given a source id from a specific source (e.g. nlm-mesh, lc-subjects), return its alternative labels."""
        return self.ids_to_alternative_labels[source][source_id]
