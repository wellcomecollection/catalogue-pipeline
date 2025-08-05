import re
from collections import defaultdict

from ingestor.models.display.availability import get_display_availability
from ingestor.models.display.identifier import get_display_identifier
from ingestor.models.display.location import get_display_location
from ingestor.models.indexable_work import (
    DisplayConcept,
    DisplayContributionRole,
    DisplayContributor,
    DisplayDigitalLocation,
    DisplayGenre,
    DisplayHoldings,
    DisplayId,
    DisplayIdentifier,
    DisplayIdLabel,
    DisplayItem,
    DisplayNote,
    DisplayPhysicalLocation,
    DisplayProductionEvent,
    DisplayRelation,
    DisplaySubject,
)


def get_display_identifiers(
    source_identifier: dict | None, other_identifiers: list[dict]
):
    identifiers = []

    raw_identifiers = []
    if source_identifier is not None:
        raw_identifiers.append(source_identifier)

    raw_identifiers += other_identifiers

    for raw in raw_identifiers:
        display_id = get_display_identifier(raw["value"], raw["identifierType"]["id"])
        identifiers.append(display_id)

    return identifiers


def display_identifiers(raw_item_id: dict):
    source_identifier = raw_item_id.get("sourceIdentifier")
    other_identifiers = raw_item_id.get("otherIdentifiers", [])
    return get_display_identifiers(source_identifier, other_identifiers)


def natural_sort_key(key: str | None) -> list[int | str]:
    """
    A sort key which can be used to sort strings containing number sequences in a 'natural' way, where lower
    numbers come first. For example, 'A/10/B' < 'A/90/B', but 'A/10/B' > 'A/9/B'.
    """
    return [int(c) if c.isdigit() else c for c in re.split(r"(\d+)", key or "")]


class RawNeptuneWork:
    def __init__(
        self, es_work: dict, work_hierarchy: dict | None, work_concepts: list | None
    ):
        self.work_data = es_work.get("data", {})
        self.work_state = es_work["state"]
        self.work_hierarchy = work_hierarchy or {}
        self.work_concepts = work_concepts or []

        self.title: str | None = self.work_data.get("title")
        self.lettering: str | None = self.work_data.get("lettering")
        self.edition: str | None = self.work_data.get("edition")
        self.duration: int | None = self.work_data.get("duration")
        self.reference_number: str | None = self.work_data.get("referenceNumber")
        self.physical_description: str | None = self.work_data.get(
            "physicalDescription"
        )
        self.alternative_titles: list[str] = self.work_data.get("alternativeTitles", [])
        self.description: str | None = self.work_data.get("description")
        self.current_frequency: str | None = self.work_data.get("currentFrequency")
        self.former_frequency: list[str] = self.work_data.get("formerFrequency", [])
        self.designation: list[str] = self.work_data.get("designation", [])

    @property
    def wellcome_id(self) -> str:
        wellcome_id: str = self.work_state["canonicalId"]
        assert len(wellcome_id) == 8
        return wellcome_id

    @property
    def display_identifiers(self) -> list[DisplayIdentifier]:
        source_identifier = self.work_state["sourceIdentifier"]
        other_identifiers = self.work_data.get("otherIdentifiers", [])
        return get_display_identifiers(source_identifier, other_identifiers)

    @property
    def source_identifier(self) -> str:
        return self.display_identifiers[0].value

    @property
    def other_identifiers(self) -> list[str]:
        return [i.value for i in self.display_identifiers]

    @property
    def thumbnail(self) -> DisplayDigitalLocation | None:
        thumbnail = self.work_data.get("thumbnail")
        if thumbnail is None:
            return None

        location = get_display_location(thumbnail)
        assert isinstance(location, DisplayDigitalLocation)
        return location

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
    def display_languages(self) -> list[DisplayIdLabel]:
        languages = []
        for language in self.work_data.get("languages", []):
            languages.append(DisplayIdLabel(**language, type="Language"))

        return languages

    @property
    def languages(self) -> list[str]:
        return [lang.label for lang in self.display_languages]

    @property
    def display_notes(self) -> list[DisplayNote]:
        grouped_notes = defaultdict(list)
        for note in self.work_data.get("notes", []):
            grouped_notes[note["noteType"]["id"]].append(note)
        
        notes = []
        for note_type, group in grouped_notes.items():
            notes.append(
                DisplayNote(
                    contents=[note["contents"] for note in group],
                    noteType=DisplayIdLabel(**group[0]["noteType"], type="NoteType"),
                )
            )            

        for note in self.work_data.get("notes", []):
            notes.append(
                DisplayNote(
                    contents=[note["contents"]],
                    noteType=DisplayIdLabel(**note["noteType"], type="NoteType"),
                )
            )

        return notes

    @property
    def notes(self) -> list[str]:
        contents = []
        for note in self.work_data.get("notes", []):
            contents.append(note["contents"])
        
        return contents

    @property
    def created_date(self) -> DisplayConcept | None:
        created_date = self.work_data.get("createdDate")
        if created_date is None:
            return None

        return DisplayConcept(label=created_date["label"], type="Period")

    @property
    def display_items(self) -> list[DisplayItem]:
        items = []

        for item in self.work_data.get("items", []):
            locations = [get_display_location(loc) for loc in item.get("locations")]
            items.append(
                DisplayItem(
                    id=item["id"].get("canonicalId"),
                    identifiers=display_identifiers(item["id"]),
                    title=item.get("title"),
                    note=item.get("note"),
                    locations=locations,
                )
            )

        return items

    @property
    def item_ids(self) -> list[str]:
        return [item.id for item in self.display_items if item.id is not None]

    @property
    def item_identifiers(self) -> list[str]:
        identifiers = []
        for item in self.display_items:
            identifiers.extend([identifier.value for identifier in item.identifiers])

        return identifiers

    @property
    def item_shelfmarks(self) -> list[str]:
        # Shelfmarks are only available on physical locations
        physical_locations = []
        for item in self.display_items:
            physical_locations.extend(
                [
                    loc
                    for loc in item.locations
                    if isinstance(loc, DisplayPhysicalLocation)
                ]
            )

        return [loc.shelfmark for loc in physical_locations if loc.shelfmark is not None]

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
    def display_production(self) -> list[DisplayProductionEvent]:
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

    @property
    def production_labels(self) -> list[str]:
        concepts = []
        for event in self.display_production:
            concepts.extend(event.places + event.agents + event.dates)

        return [c.label for c in concepts]

    def _get_display_relation(self, raw_related: dict, total_parts: int):
        return DisplayRelation(
            id=raw_related["id"],
            title=raw_related["title"],
            referenceNumber=raw_related["reference"],
            totalParts=total_parts,
        )

    @property
    def display_part_of(self) -> list[DisplayRelation]:
        if "parent" not in self.work_hierarchy:
            return []

        sibling_count = len(self.work_hierarchy.get("siblings", [])) + 1
        parent = self._get_display_relation(
            self.work_hierarchy["parent"], sibling_count
        )

        # TODO: Handle series
        return [parent]

    @property
    def part_of_titles(self) -> list[str]:
        return [p.title for p in self.display_part_of]

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
        return DisplayConcept(
            id=raw_concept["id"]["canonicalId"],
            label=raw_concept["label"].removesuffix("."), # TODO: Should we remove the suffix here?
            identifiers=display_identifiers(raw_concept["id"]),
            type=raw_concept["type"],
        )

    @property
    def display_contributors(self) -> list[DisplayContributor]:
        contributors = []

        for raw_contributor in self.work_data["contributors"]:
            roles = []
            for raw_role in raw_contributor["roles"]:
                roles.append(DisplayContributionRole(label=raw_role["label"]))

            contributors.append(
                DisplayContributor(
                    agent=self._get_display_concept(raw_contributor["agent"]),
                    roles=roles,
                    primary=raw_contributor["primary"],
                )
            )

        return contributors
    
    @property
    def contributor_labels(self) -> list[str]:
        return [c.agent.label for c in self.display_contributors]

    @property
    def display_genres(self) -> list[DisplayGenre]:
        genres = []

        for raw_genre in self.work_data["genres"]:
            concepts = []
            for raw_concept in raw_genre["concepts"]:
                concepts.append(DisplayConcept(
                    id=raw_concept["id"]["canonicalId"],
                    label=raw_concept["label"].removesuffix("."), # TODO: Should we remove the suffix here?
                    identifiers=display_identifiers(raw_concept["id"]),
                    type="Genre",
                ))

            genres.append(DisplayGenre(concepts=concepts, label=raw_genre["label"]))

        return genres

    @property
    def genre_labels(self) -> list[str]:
        labels = []
        for genre in self.display_genres:
            labels += [c.label for c in genre.concepts]

        return labels

    @property
    def display_subjects(self) -> list[DisplaySubject]:
        subjects = []

        for raw_subject in self.work_data["subjects"]:
            concepts = []
            for raw_concept in raw_subject["concepts"]:
                concepts.append(self._get_display_concept(raw_concept))
            subjects.append(
                DisplaySubject(
                    label=raw_subject["label"], id=raw_subject["id"]["canonicalId"], concepts=concepts
                )
            )

        return subjects

    @property
    def subject_labels(self) -> list[str]:
        labels = []
        for subject in self.display_subjects:
            labels += [c.label for c in subject.concepts]

        return labels

    @property
    def collection_path(self) -> str | None:
        collection_path: str | None = self.work_data.get("collectionPath", {}).get(
            "path"
        )
        return collection_path

    @property
    def collection_path_label(self) -> str | None:
        collection_path_label: str | None = self.work_data.get(
            "collectionPath", {}
        ).get("label")
        return collection_path_label

    @property
    def image_ids(self) -> list[str]:
        images = self.work_data.get("imageData", [])
        return [image["id"]["canonicalId"] for image in images]

    @property
    def image_source_identifiers(self) -> list[str]:
        images = self.work_data.get("imageData", [])
        image_identifiers = []
        for image in images:
            image_identifiers += display_identifiers(image["id"])

        return [i.value for i in image_identifiers]

    @property
    def availabilities(self) -> list[DisplayIdLabel]:
        availabilities = []
        for raw_availability in self.work_state.get("availabilities", []):
            availabilities.append(
                get_display_availability(raw_availability["id"])
            )
        
        return availabilities

    # TODO: Recursive partOf
