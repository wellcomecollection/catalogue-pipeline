from dataclasses import field
from typing import Optional

from pydantic import BaseModel

from models.graph_node import ConceptType


class ConceptsQueryResult(BaseModel):
    concepts: list[dict]
    related_to: dict[str, list]
    fields_of_work: dict[str, list]
    narrower_than: dict[str, list]
    broader_than: dict[str, list]
    people: dict[str, list]
    referenced_together: dict[str, list]
    frequent_collaborators: dict[str, list]
    related_topics: dict[str, list]


class ConceptsQuerySingleResult(BaseModel):
    concept: dict
    related_to: list[dict]
    fields_of_work: list[dict]
    narrower_than: list[dict]
    broader_than: list[dict]
    people: list[dict]
    referenced_together: list[dict]
    frequent_collaborators: list[dict]
    related_topics: list[dict]


class CatalogueConceptIdentifier(BaseModel):
    value: str
    identifierType: str


class CatalogueConceptRelatedTo(BaseModel):
    label: str
    id: str
    relationshipType: str | None
    conceptType: str


def standardise_label(label: str | None) -> str | None:
    if label is None or len(label) < 1:
        return label

    capitalised = label[:1].upper() + label[1:]

    # Normalise LoC labels. (e.g. 'Sanitation--history' -> 'Sanitation - history').
    return capitalised.replace("--", " - ")


def get_priority_label(
    concept_node: dict, source_concept_nodes: list[dict]
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

    # Sources sorted by priority. Wikidata is prioritised over Library of Congress Names since Wikidata person names
    # work better as theme page titles (e.g. 'Florence Nightingale' vs 'Nightingale, Florence, 1820-1910').
    for source in ["nlm-mesh", "lc-subjects", "wikidata", "lc-names", "label-derived"]:
        if (value := labels.get(source)) is not None:
            return value, source

    raise ValueError(
        f"Concept {concept_node['properties']['id']} does not have a label."
    )


def transform_related_concepts(
    related_items: list[dict], used_labels: set[str]
) -> list[CatalogueConceptRelatedTo]:
    """
    Process each related concept, extracting its highest-priority label and the relationship type.
    """
    processed_items = []

    for related_item in related_items:
        concept_id = related_item["concept_node"]["~properties"]["id"]
        label, _ = get_priority_label(
            related_item["concept_node"], related_item["source_concept_nodes"]
        )

        relationship_type = ""
        if "edge" in related_item:
            relationship_type = related_item["edge"]["~properties"].get(
                "relationship_type", ""
            )

        if not related_item.get("concept_types"):
            concept_type: ConceptType = "Concept"
        else:
            concept_type = get_most_specific_concept_type(related_item["concept_types"])

        if label.lower() not in used_labels:
            used_labels.add(label.lower())
            processed_items.append(
                CatalogueConceptRelatedTo(
                    id=concept_id,
                    label=label,
                    relationshipType=relationship_type,
                    conceptType=concept_type,
                )
            )

    return processed_items


def get_most_specific_concept_type(concept_types: list[ConceptType]) -> ConceptType:
    """If a concept is classified under more than one type, pick the most specific one and return it."""

    # Prioritise concepts, with more specific ones (e.g. 'Person') above less specific ones (e.g. 'Agent').
    # Sometimes a concept is classified under types which are mutually exclusive. For example, there are
    # several hundred concepts categorised as both a 'Person' and an 'Organisation'. These inconsistencies
    # arise upstream, and we cannot easily resolve them here. To mitigate this issue, the priority list below
    # is ordered to maximise the probability of choosing the right type based on an analysis of current inconsistencies.
    # (For example, when a concept is categorised as both an 'Organisation' and a 'Place', the 'Place' type is almost
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


class ConceptDescription(BaseModel):
    text: str
    sourceLabel: str
    sourceUrl: str


def get_concept_description(concept_data: dict) -> ConceptDescription | None:
    source_concept_nodes = concept_data["source_concepts"]

    for source_concept in source_concept_nodes:
        properties = source_concept["~properties"]
        description_text = standardise_label(properties.get("description"))
        description_source = properties["source"]
        source_concept_id = properties["id"]

        # Only extract descriptions from Wikidata (MeSH also stores descriptions, but we do not want to surface them).
        if description_text is not None and description_source == "wikidata":
            return ConceptDescription(
                text=description_text,
                sourceLabel=description_source,
                sourceUrl=get_source_concept_url(source_concept_id, description_source),
            )

    return None


class RelatedConcepts(BaseModel):
    relatedTo: list[CatalogueConceptRelatedTo]
    fieldsOfWork: list[CatalogueConceptRelatedTo]
    narrowerThan: list[CatalogueConceptRelatedTo]
    broaderThan: list[CatalogueConceptRelatedTo]
    people: list[CatalogueConceptRelatedTo]
    referencedTogether: list[CatalogueConceptRelatedTo]
    frequentCollaborators: list[CatalogueConceptRelatedTo]
    relatedTopics: list[CatalogueConceptRelatedTo]


class CatalogueConcept(BaseModel):
    id: str
    identifiers: list[CatalogueConceptIdentifier] = field(default_factory=list)
    label: str
    alternativeLabels: list[str] = field(default_factory=list)
    description: Optional[ConceptDescription]
    type: ConceptType
    sameAs: list[str]
    relatedConcepts: RelatedConcepts

    @classmethod
    def from_neptune_result(cls, data: ConceptsQuerySingleResult) -> "CatalogueConcept":
        identifiers = []
        alternative_labels = set()

        concept_data: dict = data.concept

        label, _ = get_priority_label(
            concept_data["concept"], concept_data["source_concepts"]
        )

        for source_concept in concept_data["linked_source_concepts"]:
            properties = source_concept["~properties"]
            source = properties["source"]

            identifiers.append(
                CatalogueConceptIdentifier(
                    value=properties["id"],
                    identifierType=source,
                )
            )

        # Extract alternative labels from _all_ source concepts (utilising both HAS_SOURCE_CONCEPT and SAME_AS edges)
        for source_concept in concept_data["source_concepts"]:
            for alternative_label in (
                source_concept["~properties"].get("alternative_labels", "").split("||")
            ):
                if len(alternative_label) > 0:
                    standardised_label = standardise_label(alternative_label)
                    if standardised_label is not None:
                        alternative_labels.add(standardised_label)

        # The `used_labels` set is used to ensure that a given related concept is only listed once. For example,
        # if a given concept is listed in both `broader_than` and `referenced_together`, we only want to keep
        # one of those references to prevent duplication in the frontend.
        used_labels = {label.lower()}

        # Concepts which are not connected to any Works will not have any types associated with them. We periodically
        # remove such concepts from the graph, but there might be a few of them at any given point.
        if not concept_data.get("concept_types"):
            concept_type: ConceptType = "Concept"
        else:
            concept_type = get_most_specific_concept_type(concept_data["concept_types"])

        return CatalogueConcept(
            id=concept_data["concept"]["~properties"]["id"],
            type=concept_type,
            label=label,
            alternativeLabels=sorted(list(set(alternative_labels))),
            description=get_concept_description(concept_data),
            identifiers=identifiers,
            sameAs=concept_data["same_as_concept_ids"],
            relatedConcepts=RelatedConcepts(
                fieldsOfWork=transform_related_concepts(
                    data.fields_of_work, used_labels
                ),
                people=transform_related_concepts(data.people, used_labels),
                narrowerThan=transform_related_concepts(
                    data.narrower_than, used_labels
                ),
                broaderThan=transform_related_concepts(data.broader_than, used_labels),
                frequentCollaborators=transform_related_concepts(
                    data.frequent_collaborators, used_labels
                ),
                relatedTopics=transform_related_concepts(
                    data.related_topics, used_labels
                ),
                relatedTo=transform_related_concepts(data.related_to, used_labels),
                referencedTogether=transform_related_concepts(
                    data.referenced_together, used_labels
                ),
            ),
        )
