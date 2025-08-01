from ingestor.models.display.identifier import get_display_identifier
from ingestor.models.indexable import DisplayIdentifier
from ingestor.models.indexable_concept import (
    ConceptDescription,
    ConceptIdentifier,
)
from shared.types import ConceptType

# Sources sorted by priority for querying purposes.
QUERY_SOURCE_PRIORITY = [
    "nlm-mesh",
    "lc-subjects",
    "lc-names",
    "wikidata",
    "label-derived",
]

# Sources sorted by priority for display purposes. Wikidata is prioritised over Library of Congress Names since Wikidata
# person names work better as theme page titles (e.g. 'Florence Nightingale' vs 'Nightingale, Florence, 1820-1910').
DISPLAY_SOURCE_PRIORITY = [
    "nlm-mesh",
    "lc-subjects",
    "wikidata",
    "lc-names",
    "label-derived",
]


class MissingLabelError(ValueError):
    pass


def standardise_label(label: str | None) -> str | None:
    if label is None or len(label) < 1:
        return label

    capitalised = label[:1].upper() + label[1:]

    # Normalise LoC labels. (e.g. 'Sanitation--history' -> 'Sanitation - history').
    return capitalised.replace("--", " - ")


def get_source_concept_url(source_concept_id: str, source: str) -> str:
    if source == "nlm-mesh":
        return f"https://meshb.nlm.nih.gov/record/ui?ui={source_concept_id}"
    if source == "lc-subjects":
        return f"https://id.loc.gov/authorities/subjects/{source_concept_id}.html"
    if source == "lc-names":
        return f"https://id.loc.gov/authorities/names/{source_concept_id}.html"
    if source == "wikidata":
        return f"https://www.wikidata.org/wiki/{source_concept_id}"

    raise ValueError(f"Unknown source: {source}")


def get_priority_label(
    concept_node: dict,
    source_concept_nodes: list[dict],
    source_priority: list[str],
) -> tuple[str, str]:
    """
    Given a concept and its source concepts, extract the corresponding labels and return the highest-priority one.
    (For example, if a `label` field exists in both Wikidata and MeSH, we always prioritise the MeSH one.)
    """
    labels = {"label-derived": concept_node["~properties"].get("label")}

    for source_concept in source_concept_nodes:
        properties = source_concept["~properties"]
        source = properties["source"]
        labels[source] = standardise_label(properties.get("label"))

    for source in source_priority:
        if (value := labels.get(source)) is not None:
            return value, source

    raise MissingLabelError(
        f"Concept {concept_node['~properties']['id']} does not have a label."
    )


def get_most_specific_concept_type(concept_types: list[str]) -> ConceptType:
    # Concepts which are not connected to any Works will not have any types associated with them. We periodically
    # remove such concepts from the graph, but there might be a few of them at any given point.
    if len(concept_types) == 0:
        return "Concept"

    # Prioritise concepts, with more specific ones (e.g. 'Person') above less specific ones (e.g. 'Agent').
    # Sometimes a concept is classified under types which are mutually exclusive. For example, there are
    # several hundred concepts categorised as both a 'Person' and an 'Organisation'. These inconsistencies arise
    # upstream, and we cannot easily resolve them here. To mitigate this issue, the priority list below is ordered
    # to maximise the probability of choosing the right type based on an analysis of current inconsistencies. (For
    # example, when a concept is categorised as both an 'Organisation' and a 'Place', the 'Place' type is almost
    # always the correct one, which is why 'Place' is higher in the priority list than 'Organisation').
    concept_types_by_priority: list[ConceptType] = [
        "Genre",
        "Place",
        "Person",
        "Organisation",
        "Period",
        "Meeting",
        "Agent",
        "Subject",
        "Concept",
    ]

    for concept_type in concept_types_by_priority:
        if concept_type in concept_types:
            return concept_type

    raise ValueError(f"Invalid set of concept types: {concept_types}.")


class RawNeptuneConcept:
    def __init__(self, neptune_concept: dict, all_related_concepts: dict):
        self.raw_concept = neptune_concept
        self.raw_related_concepts = all_related_concepts

    @property
    def wellcome_id(self) -> str:
        wellcome_id = self.raw_concept["concept"]["~properties"]["id"]
        assert isinstance(wellcome_id, str)
        return wellcome_id

    @property
    def label(self) -> str:
        concept_node = self.raw_concept["concept"]
        source_concept_nodes = self.raw_concept["source_concepts"]

        label, _ = get_priority_label(
            concept_node, source_concept_nodes, QUERY_SOURCE_PRIORITY
        )

        assert isinstance(label, str)
        return label

    @property
    def display_label(self) -> str:
        concept = self.raw_concept["concept"]
        source_concepts = self.raw_concept["source_concepts"]

        display_label, _ = get_priority_label(
            concept, source_concepts, DISPLAY_SOURCE_PRIORITY
        )

        return display_label

    @property
    def same_as(self) -> list[str]:
        same_as = self.raw_concept["same_as_concept_ids"]
        assert isinstance(same_as, list)
        return same_as

    @property
    def concept_type(self) -> ConceptType:
        """If a concept is classified under more than one type, pick the most specific one and return it."""
        concept_types = self.raw_concept.get("concept_types", [])
        return get_most_specific_concept_type(concept_types)

    @property
    def identifiers(self) -> list[ConceptIdentifier]:
        ids = []

        for source_concept in self.raw_concept["linked_source_concepts"]:
            properties = source_concept["~properties"]
            identifier = ConceptIdentifier(
                value=properties["id"], identifierType=properties["source"]
            )
            ids.append(identifier)

        return ids

    @property
    def display_identifiers(self) -> list[DisplayIdentifier]:
        display_ids = []
        for identifier in self.identifiers:
            display_ids.append(
                get_display_identifier(identifier.value, identifier.identifierType)
            )

        return display_ids

    @property
    def alternative_labels(self) -> list[str]:
        alternative_labels: set[str] = set()
        for source_concept in self.raw_concept["source_concepts"]:
            raw_labels = source_concept["~properties"].get("alternative_labels", "")
            for alternative_label in raw_labels.split("||"):
                if len(alternative_label) > 0:
                    standardised_label = standardise_label(alternative_label)
                    if standardised_label is not None:
                        alternative_labels.add(standardised_label)

        return sorted(list(alternative_labels))

    @property
    def description(self) -> ConceptDescription | None:
        for source_concept in self.raw_concept["source_concepts"]:
            properties = source_concept["~properties"]
            description_text = standardise_label(properties.get("description"))

            description_source = properties["source"]
            source_concept_id = properties["id"]

            # Only extract descriptions from Wikidata (MeSH also stores descriptions, but we should not surface them).
            if description_text is not None and description_source == "wikidata":
                return ConceptDescription(
                    text=description_text,
                    sourceLabel=description_source,
                    sourceUrl=get_source_concept_url(
                        source_concept_id, description_source
                    ),
                )

        return None
