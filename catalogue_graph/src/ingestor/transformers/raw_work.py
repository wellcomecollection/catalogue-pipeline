import re
from collections import defaultdict
from collections.abc import Generator

from ingestor.models.denormalised.work import (
    AllIdentifiers,
    DenormalisedWork,
)
from ingestor.models.display.availability import DisplayAvailability
from ingestor.models.display.concept import (
    DisplayConcept,
    DisplayContributionRole,
    DisplayContributor,
    DisplayGenre,
    DisplaySubject,
)
from ingestor.models.display.holdings import DisplayHoldings
from ingestor.models.display.id_label import DisplayId, DisplayIdLabel
from ingestor.models.display.identifier import DisplayIdentifier
from ingestor.models.display.item import DisplayItem
from ingestor.models.display.location import (
    DisplayDigitalLocation,
    DisplayLocation,
    DisplayPhysicalLocation,
)
from ingestor.models.display.note import DisplayNote
from ingestor.models.display.production_event import DisplayProductionEvent
from ingestor.models.display.relation import DisplayRelation


def natural_sort_key(key: str | None) -> list[int | str]:
    """
    A sort key which can be used to sort strings containing number sequences in a 'natural' way, where lower
    numbers come first. For example, 'A/10/B' < 'A/90/B', but 'A/10/B' > 'A/9/B'.
    """
    return [int(c) if c.isdigit() else c for c in re.split(r"(\d+)", key or "")]


class RawNeptuneWork:
    def __init__(
        self,
        denormalised_work: DenormalisedWork,
        work_hierarchy: dict | None,
        work_concepts: list | None,
    ):
        self.data = denormalised_work.data
        self.state = denormalised_work.state
        self.work_hierarchy = work_hierarchy or {}
        self.work_concepts = work_concepts or []

    @property
    def display_identifiers(self) -> list[DisplayIdentifier]:
        all_ids = AllIdentifiers(
            canonicalId=self.state.canonicalId,
            sourceIdentifier=self.state.sourceIdentifier,
            otherIdentifiers=self.data.otherIdentifiers,
        )
        return DisplayIdentifier.from_all_identifiers(all_ids)

    @property
    def other_identifiers(self) -> list[str]:
        return [i.value for i in self.data.otherIdentifiers]

    @property
    def thumbnail(self) -> DisplayDigitalLocation | None:
        if self.data.thumbnail is None:
            return None

        location: DisplayDigitalLocation = DisplayLocation.from_location(
            self.data.thumbnail
        )
        return location

    @property
    def work_type(self) -> DisplayIdLabel | None:
        if self.data.format is None:
            return None

        return DisplayIdLabel.from_id_label(self.data.format, "Format")

    @property
    def display_languages(self) -> list[DisplayIdLabel]:
        return [
            DisplayIdLabel.from_id_label(i, "Language") for i in self.data.languages
        ]

    @property
    def display_notes(self) -> Generator[DisplayNote]:
        grouped_notes = defaultdict(list)
        for note in self.data.notes:
            grouped_notes[note.noteType.id].append(note)

        for group in grouped_notes.values():
            yield DisplayNote(
                contents=[note.contents for note in group],
                noteType=DisplayIdLabel.from_id_label(group[0].noteType, "NoteType"),
            )

    @property
    def created_date(self) -> DisplayConcept | None:
        if self.data.createdDate is None:
            return None

        return DisplayConcept(label=self.data.createdDate.label, type="Period")

    @property
    def display_items(self) -> list[DisplayItem]:
        for item in self.data.items:
            yield DisplayItem(
                id=item.id.canonicalId,
                identifiers=DisplayIdentifier.from_all_identifiers(item.id),
                title=item.title,
                note=item.note,
                locations=[
                    DisplayLocation.from_location(loc) for loc in item.locations
                ],
            )

    @property
    def item_ids(self) -> list[str]:
        return [
            item.id.canonicalId
            for item in self.data.items
            if item.id.canonicalId is not None
        ]

    @property
    def item_identifiers(self) -> Generator[str]:
        for item in self.display_items:
            for identifier in item.identifiers:
                yield identifier.value

    @property
    def item_shelfmarks(self) -> Generator[str]:
        for item in self.display_items:
            for loc in item.locations:
                # Shelfmarks are only available on physical locations
                if (
                    isinstance(loc, DisplayPhysicalLocation)
                    and loc.shelfmark is not None
                ):
                    yield loc.shelfmark

    @property
    def holdings(self) -> Generator[DisplayHoldings]:
        for holding in self.data.holdings:
            location = None
            if holding.location is not None:
                location = DisplayLocation.from_location(holding.location)

            yield DisplayHoldings(
                note=holding.note,
                enumeration=holding.enumeration,
                location=location,
            )

    @property
    def images(self) -> Generator[DisplayId]:
        for image in self.data.imageData:
            yield DisplayId(id=image.id.canonicalId, type="Image")

    @property
    def display_production(self) -> Generator[DisplayProductionEvent]:
        for event in self.data.production:
            yield DisplayProductionEvent(
                label=event.label,
                places=[
                    DisplayConcept(label=p.label, type="Place") for p in event.places
                ],
                agents=[
                    DisplayConcept(label=a.label, type="Agent") for a in event.agents
                ],
                dates=[
                    DisplayConcept(label=p.label, type="Period") for p in event.dates
                ],
                function=(
                    DisplayConcept(label=event.function.label)
                    if event.function
                    else None
                ),
            )

    @property
    def production_labels(self) -> list[str]:
        for event in self.display_production:
            for concept in event.places + event.agents + event.dates:
                yield concept.label

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

    @property
    def display_contributors(self) -> Generator[DisplayContributor]:
        for contributor in self.data.contributors:
            yield DisplayContributor(
                agent=DisplayConcept.from_concept(contributor.agent),
                roles=[
                    DisplayContributionRole(label=r.label) for r in contributor.roles
                ],
                primary=contributor.primary,
            )

    @property
    def display_genres(self) -> Generator[DisplayGenre]:
        for raw_genre in self.data.genres:
            concepts = [DisplayConcept.from_concept(c) for c in raw_genre.concepts]
            yield DisplayGenre(concepts=concepts, label=raw_genre.label)

    @property
    def genre_labels(self) -> list[str]:
        for genre in self.display_genres:
            for concept in genre.concepts:
                yield concept.label

    @property
    def display_subjects(self) -> list[DisplaySubject]:
        for subject in self.data.subjects:
            yield DisplaySubject(
                label=subject.label,
                id=subject.id.canonicalId,
                concepts=[DisplayConcept.from_concept(c) for c in subject.concepts],
            )

    @property
    def subject_labels(self) -> list[str]:
        for subject in self.display_subjects:
            for concept in subject.concepts:
                yield concept.label

    @property
    def image_ids(self) -> list[str]:
        return [image.id.canonicalId for image in self.data.imageData]

    @property
    def image_source_identifiers(self) -> Generator[str]:
        for image in self.data.imageData:
            for display_identifier in DisplayIdentifier.from_all_identifiers(image.id):
                yield display_identifier.value

    @property
    def availabilities(self) -> list[DisplayIdLabel]:
        return [
            DisplayAvailability.from_availability(a) for a in self.state.availabilities
        ]

    # TODO: Recursive partOf
