from typing import Any

from models.graph_node import ConceptType
from models.indexable import DisplayIdentifier
from models.indexable_concept import (
    ConceptDescription,
    ConceptIdentifier,
)

from elasticsearch_transformers.display_identifier import get_display_identifier


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


def get_priority_source_concept_value(
        concept_node: dict | None, source_concept_nodes: list[dict], key: str
) -> tuple[Any, str | None]:
    """
    Given a concept, its source concepts, and a key (e.g. 'label' or 'description'), extract the corresponding
    values (where available) and return the highest-priority one.
    (For example, if a `description` field exists in both Wikidata and MeSH, we always prioritise the MeSH one.)
    """
    values = {}

    if concept_node is not None:
        values["label-derived"] = concept_node["~properties"].get(key, "")

    for source_concept in source_concept_nodes:
        properties = source_concept["~properties"]
        source = properties["source"]
        values[source] = standardise_label(properties.get(key))

    # Sources sorted by priority
    for source in ["nlm-mesh", "lc-subjects", "lc-names", "wikidata", "label-derived"]:
        if (value := values.get(source)) is not None:
            return value, source

    return None, None


def get_most_specific_concept_type(concept_types: list[str]) -> ConceptType:
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
        linked_source_nodes = self.raw_concept["linked_source_concepts"]

        # For now, only extract labels from source concepts which are linked to the concept via HAS_SOURCE_CONCEPT edges
        label, _ = get_priority_source_concept_value(
            concept_node, linked_source_nodes, "label"
        )

        assert isinstance(label, str)
        return label

    @property
    def same_as(self) -> list[str]:
        same_as = self.raw_concept["same_as_concept_ids"]
        assert isinstance(same_as, list)
        return same_as

    @property
    def concept_type(self) -> ConceptType:
        """If a concept is classified under more than one type, pick the most specific one and return it."""

        # Concepts which are not connected to any Works will not have any types associated with them. We periodically
        # remove such concepts from the graph, but there might be a few of them at any given point.
        concept_types = self.raw_concept.get("concept_types", ["Concept"])
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
        # Extract alternative labels from _all_ source concepts (utilising both HAS_SOURCE_CONCEPT and SAME_AS edges)
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
        source_concept_nodes = self.raw_concept["source_concepts"]
        text, source = get_priority_source_concept_value(
            None, source_concept_nodes, "description"
        )

        if text and source:
            source_concept_id: str | None = None
            for source_concept in source_concept_nodes:
                if source_concept["~properties"]["source"] == source:
                    source_concept_id = source_concept["~properties"]["id"]

            assert source_concept_id is not None

            return ConceptDescription(
                text=text,
                sourceLabel=source,
                sourceUrl=get_source_concept_url(source_concept_id, source),
            )

        return None
