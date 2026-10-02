import os
import re
import unicodedata
from collections import defaultdict

from adapters.transformers.utils.text_utils import trim_trailing_period
from models.events import BasePipelineEvent, BulkLoaderEvent
from utils.aws import get_csv_from_s3
from utils.types import ConceptSource, ConceptType, TransformerType

AGENT_TYPES = ("Person", "Agent", "Organisation")

# 'weco-authority' is deliberately absent: see `IdLabelChecker.__init__`.
LABEL_MATCH_SOURCES_BY_PRIORITY: list[ConceptSource] = [
    "nlm-mesh",
    "lc-subjects",
    "lc-names",
]
AMBIGUITY_THRESHOLD = 1

WECO_ID_PREFIX = "weco:"

with open(f"{os.path.dirname(__file__)}/data/concept_label_deny_list.txt") as f:
    CONCEPT_DENY_LIST = [line.strip().lower() for line in f]


def _concept_source_from_id(source_id: str) -> ConceptSource:
    if source_id.startswith(WECO_ID_PREFIX):
        return "weco-authority"
    if source_id[0] == "n":
        return "lc-names"
    if source_id[0] == "s":
        return "lc-subjects"
    if source_id[0] == "D":
        return "nlm-mesh"

    raise ValueError(f"Unexpected source id {source_id}")


def _label_tokens(label: str) -> list[str]:
    folded = unicodedata.normalize("NFKD", label).encode("ascii", "ignore").decode()
    return [token for token in re.split(r"[^a-z0-9]+", folded.lower()) if token]


def _name_tokens(heading_tokens: list[str]) -> list[str]:
    """
    The tokens of an LC Names heading before its first date. Name-title headings append the title
    after the dates ("January, Brendan, 1972- Da Vinci"), and the title must not count as the name.
    """
    for index, token in enumerate(heading_tokens):
        if token.isdigit():
            return heading_tokens[:index] or heading_tokens

    return heading_tokens


def _is_plausible_name_alias(label: str, preferred_label: str) -> bool:
    """
    LC Names aliases are mostly RDA date variants of the preferred form ("Gerrish, Samuel, d. 1741"
    for "Gerrish, Samuel, -1741"). An alias sharing no token with the preferred name is a homonym
    ("Bliss" for the ECSIS Symposium), and a bare surname cannot identify one specific person
    ("Cook" for "Cook, Stephen S."). See wellcomecollection/platform#6679 for the census.
    """
    label_tokens = _label_tokens(label)
    preferred_tokens = _label_tokens(preferred_label)

    if not set(label_tokens) & set(_name_tokens(preferred_tokens)):
        return False

    return not (len(label_tokens) == 1 and len(preferred_tokens) > 1)


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

        for transformer in transformers:
            bulk_loader_event = BulkLoaderEvent(
                transformer_type=transformer,
                entity_type="nodes",
                pipeline_date=event.pipeline_date,
                graph_date=event.graph_date,
            )
            for row in get_csv_from_s3(bulk_loader_event.get_s3_uri()):
                source_id = row[":ID"]
                label = row["label:String"].lower()
                alternative_labels = [
                    label.lower()
                    for label in row["alternative_labels:String"].split("||")
                    if label != ""
                ]

                concept_source = _concept_source_from_id(source_id)

                # Every source goes into the id-keyed dictionaries, which double as the index of
                # which ids were bulk loaded.
                self.ids_to_labels[concept_source][source_id] = label
                self.ids_to_alternative_labels[concept_source][source_id] = (
                    alternative_labels
                )

                # Only label-matched sources go into the reverse dictionaries. Most weco-authority
                # records have a blank label, which would map the empty label to a bag of weco ids.
                if concept_source in LABEL_MATCH_SOURCES_BY_PRIORITY:
                    self._add_label_mapping(label, source_id, concept_source)
                    self._add_alternative_label_mappings(
                        alternative_labels, source_id, concept_source
                    )

    def _add_label_mapping(
        self, label: str, source_id: str, concept_source: ConceptSource
    ) -> None:
        self.labels_to_ids[concept_source][self._normalise_label(label)].append(
            source_id
        )

    def _add_alternative_label_mappings(
        self, labels: list[str], source_id: str, concept_source: ConceptSource
    ) -> None:
        # Dedupe so "X" and "X." on one record do not make the key ambiguous.
        for label in dict.fromkeys(self._normalise_label(label) for label in labels):
            self.alternative_labels_to_ids[concept_source][label].append(source_id)

    def _normalise_label(self, label: str) -> str:
        # Matches the label-derived id normalisation, so "X" and "X." share a key.
        return trim_trailing_period(label.lower())

    def get_id(self, label: str, concept_type: ConceptType) -> str | None:
        """
        Given some label, return exactly one closest-matching source concept id (or 'None' if no match found).
        """
        label = self._normalise_label(label)

        # Do not attempt to match blacklisted concept labels.
        if label in CONCEPT_DENY_LIST:
            return None

        # First, try to match the concept label to a 'main' source concept label, in order of priority.
        for source in LABEL_MATCH_SOURCES_BY_PRIORITY:
            if len(source_ids := self.labels_to_ids[source][label]) > 0:
                return source_ids[0]

        # If no matches found, try matching on alternative labels
        for source in LABEL_MATCH_SOURCES_BY_PRIORITY:
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

                # MeSH and LCSH aliases are mostly plural or expanded forms of the heading and are
                # kept as they are; only LC Names aliases are checked against the preferred label.
                if source == "lc-names" and not _is_plausible_name_alias(
                    label, self.ids_to_labels[source][source_ids[0]]
                ):
                    continue

                return source_ids[0]

        return None

    def has_id(self, source_id: str, source: ConceptSource) -> bool:
        """
        Given a source id from a specific source, return 'True' if it was bulk loaded into the
        graph. Prefer this over `get_label`, which returns an empty string for a blank label.
        """
        return source_id in self.ids_to_labels[source]

    def get_label(self, source_id: str, source: ConceptSource) -> str | None:
        """Given a source id from a specific source (e.g. nlm-mesh, lc-subjects), return its label."""
        return self.ids_to_labels[source].get(source_id, None)

    def get_alternative_labels(
        self, source_id: str, source: ConceptSource
    ) -> list[str]:
        """Given a source id from a specific source (e.g. nlm-mesh, lc-subjects), return its alternative labels."""
        return self.ids_to_alternative_labels[source][source_id]
