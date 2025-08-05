import re

from ingestor.models.display.identifier import get_display_identifier
from ingestor.models.display.location import get_display_location
from ingestor.models.indexable_work import (
    DisplayConcept,
    DisplayContributor,
    DisplayDigitalLocation,
    DisplayGenre,
    DisplayHoldings,
    DisplayId,
    DisplayIdentifier,
    DisplayIdLabel,
    DisplayItem,
    DisplayNote,
    DisplayProductionEvent,
    DisplayRelation,
    DisplaySubject,
)

from .raw_concept import DISPLAY_SOURCE_PRIORITY, MissingLabelError, get_priority_label


def get_identifiers(source_identifier: dict | None, other_identifiers: list[dict]):
    identifiers = []
    
    raw_identifiers = []
    if source_identifier is not None:
        raw_identifiers.append(source_identifier)

    raw_identifiers += other_identifiers

    for raw in raw_identifiers:
        display_id = get_display_identifier(raw["value"], raw["identifierType"]["id"])
        identifiers.append(display_id)
    
    return identifiers


def natural_sort_key(key: str):
    """
    A sort key which can be used to sort strings containing number sequences in a 'natural' way, where lower 
    numbers come first. For example, 'A/10/B' < 'A/90/B', but 'A/10/B' > 'A/9/B'.
    """
    return [int(c) if c.isdigit() else c for c in re.split(r"(\d+)", key)]


class RawNeptuneWork:
    def __init__(self, es_work: dict, work_hierarchy: dict | None, work_concepts: list | None):
        self.work_data = es_work.get("data", {})
        self.work_state = es_work["state"]
        self.work_hierarchy = work_hierarchy or {}
        self.raw_work_concepts = work_concepts or []

    def _extract_raw_concepts(self, referenced_in: str) -> list[dict]:
        return [
            c for c in self.raw_work_concepts if c["referenced_in"] == referenced_in
        ]

    @property
    def wellcome_id(self) -> str:
        wellcome_id: str = self.work_state["canonicalId"]
        return wellcome_id

    @property
    def title(self) -> str:
        title: str = self.work_data.get("title", "")
        return title
    
    # TODO
    @property
    def identifiers(self) -> list[DisplayIdentifier]:
        source_identifier = self.work_state["sourceIdentifier"] 
        other_identifiers = self.work_data.get("otherIdentifiers", [])
        return get_identifiers(source_identifier, other_identifiers)

    @property
    def reference_number(self) -> str | None:
        reference_number: str | None = self.work_data.get("referenceNumber")
        return reference_number

    @property
    def physical_description(self) -> str | None:
        physical_description: str | None = self.work_data.get("physicalDescription")
        return physical_description    

    @property
    def lettering(self) -> str | None:
        lettering: str | None = self.work_data.get("lettering")
        return lettering

    @property
    def edition(self) -> str | None:
        edition: str | None = self.work_data.get("edition")
        return edition

    @property
    def duration(self) -> int | None:
        duration: str | None = self.work_data.get("duration")
        return duration    

    @property
    def alternative_titles(self) -> list[str]:
        alternative_titles: list[str] = self.work_data.get("alternativeTitles", [])
        return alternative_titles

    @property
    def description(self) -> str | None:
        description: str | None = self.work_data.get("description")
        return description
    
    @property
    def current_frequency(self) -> str | None:
        current_frequency: str | None = self.work_data.get("currentFrequency")
        return current_frequency     

    @property
    def former_frequency(self) -> list[str]:
        former_frequency: list[str] = self.work_data.get("formerFrequency", [])
        return former_frequency

    @property
    def designation(self) -> list[str]:
        designation: list[str] = self.work_data.get("designation", [])
        return designation

    @property
    def thumbnail(self) -> DisplayDigitalLocation | None:
        thumbnail = self.work_data.get("thumbnail")
        if thumbnail is None:
            return

        return get_display_location(thumbnail)

    @property
    def work_type(self) -> DisplayIdLabel | None:
        format: dict | None = self.work_data.get("format")
        if format is None:
            return None

        return DisplayIdLabel(
            id=format["id"],
            label=format["label"],
            type="Format",
        )

    @property
    def languages(self) -> list[DisplayIdLabel]:
        languages = []
        for language in self.work_data.get("languages", []):
            languages.append(DisplayIdLabel(**language, type="Language"))

        return languages

    @property
    def notes(self) -> list[DisplayNote]:
        notes = []

        # TODO: Group by note type
        for note in self.work_data.get("notes", []):
            notes.append(
                DisplayNote(
                    contents=[note["contents"]],
                    noteType=DisplayIdLabel(**note["noteType"], type="NoteType"),
                )
            )

        return notes

    @property
    def created_date(self) -> DisplayConcept | None:
        created_date = self.work_data.get("createdDate")
        if created_date is None:
            return None

        return DisplayConcept(label=created_date["label"], type="Period")

    @property
    def items(self) -> list[DisplayItem]:
        items = []

        for item in self.work_data.get("items", []):
            source_identifier = item["id"].get("sourceIdentifier")
            other_identifiers = item["id"].get("otherIdentifiers", [])
            identifiers = get_identifiers(source_identifier, other_identifiers)

            locations = [get_display_location(loc) for loc in item.get("locations")]

            items.append(
                DisplayItem(
                    id=item["id"].get("canonicalId"),
                    identifiers=identifiers,
                    title=item.get("title"),
                    note=item.get("note"),
                    locations=locations,
                )
            )

        return items

    @property
    def holdings(self) -> list[DisplayHoldings]:
        holdings = []

        for holding in self.work_data.get("holdings", []):
            location = None
            if "location" in holding:
                location = get_display_location(holding["location"])

            holdings.append(
                DisplayHoldings(
                    note=holding.get("note"),
                    enumeration=holding.get("enumeration", []),
                    location=location,
                )
            )

        return holdings

    @property
    def images(self) -> list[DisplayId]:
        images = []
        for image in self.work_data.get("image_data", []):
            images.append(DisplayId(id=image["id"]["canonicalId"], type="Image"))

        return images

    @property
    def production(self):
        events = []
        for event in self.work_data.get("production"):
            events.append(
                DisplayProductionEvent(
                    label=event["label"],
                    places=[
                        DisplayConcept(label=p["label"], type="Place")
                        for p in event["places"]
                    ],
                    agents=[
                        DisplayConcept(label=p["label"], type="Agent")
                        for p in event["agents"]
                    ],
                    dates=[
                        DisplayConcept(label=p["label"], type="Period")
                        for p in event["dates"]
                    ],
                    function=(
                        DisplayConcept(label=event["function"]["label"])
                        if event.get("function")
                        else None
                    ),
                )
            )

        return events

    def _get_display_relation(self, raw_related: dict, total_parts: int):
        return DisplayRelation(
            id=raw_related["id"],
            title=raw_related["title"],
            referenceNumber=raw_related["reference"],
            totalParts=total_parts,
        )

    @property
    def part_of(self) -> list[DisplayRelation]:
        if "parent" not in self.work_hierarchy:
            return []

        sibling_count = len(self.work_hierarchy.get("siblings", [])) + 1
        parent = self._get_display_relation(
            self.work_hierarchy["parent"], sibling_count
        )

        # TODO: Handle series
        return [parent]

    @property
    def parts(self) -> list[DisplayRelation]:
        parts = []
        for child in self.work_hierarchy.get("children", []):
            if child["id"] is not None:
                parts.append(self._get_display_relation(child, 0))

        return sorted(
            parts, key=lambda item: natural_sort_key(item.referenceNumber or item.title)
        )

    def _get_display_concept(self, raw_concept: dict) -> DisplayConcept:
        source_concepts = raw_concept["other_source_concepts"]
        if raw_concept["linked_source_concept"] is not None:
            source_concepts.append(raw_concept["linked_source_concept"])

        identifiers = []
        for source_concept in source_concepts:
            identifiers.append(
                get_display_identifier(
                    source_concept["~properties"]["id"],
                    source_concept["~properties"]["source"],
                )
            )

        try:
            label, _ = get_priority_label(raw_concept["concept"], source_concepts, DISPLAY_SOURCE_PRIORITY)
        except MissingLabelError:
            label = ""

        # if raw_concept["referenced_type"] is None:
        #     print(raw_concept)

        return DisplayConcept(
            id=raw_concept["concept"]["~properties"]["id"],
            label=label,
            identifiers=identifiers,
            type=raw_concept["referenced_type"] or "Concept",
        )

    @property
    def contributors(self) -> list[DisplayContributor]:
        contributors = []

        for raw_contributor in self._extract_raw_concepts("contributors"):
            contributors.append(
                DisplayContributor(
                    agent=self._get_display_concept(raw_contributor),
                    roles=[],  # TODO: Is this ever populated?
                    primary=False,  # TODO: Extract this info
                )
            )

        return contributors

    @property
    def genres(self) -> list[DisplayGenre]:
        genres = []

        for raw_genre in self._extract_raw_concepts("genres"):
            concept = self._get_display_concept(raw_genre)

            # TODO: Are there genres with multiple concepts?
            genres.append(DisplayGenre(concepts=[concept], label=concept.label))

        return genres

    @property
    def subjects(self) -> list[DisplaySubject]:
        subjects = []

        for raw_subject in self._extract_raw_concepts("subjects"):
            concept = self._get_display_concept(raw_subject)
            # TODO: Handle subjects with multiple concepts
            subjects.append(
                DisplaySubject(label=concept.label, id=concept.id, concepts=[concept])
            )

        return subjects

    # TODO: Recursive partOf
    # TODO: availabilities
    # TODO: collection_path_label
