from collections import defaultdict
from collections.abc import Generator

from ingestor.extractors.works_extractor import ExtractedWork
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
)
from ingestor.models.display.note import DisplayNote
from ingestor.models.display.production_event import DisplayProductionEvent
from ingestor.models.display.relation import DisplayRelation
from ingestor.models.shared.identifier import Identifiers
from utils.sort import natural_sort_key


class DisplayWorkTransformer:
    def __init__(self, extracted: ExtractedWork):
        self.data = extracted.work.data
        self.state = extracted.work.state
        self.hierarchy = extracted.hierarchy
        self.concepts = extracted.concepts

    @property
    def identifiers(self) -> Generator[DisplayIdentifier]:
        all_ids = Identifiers(
            canonical_id=self.state.canonical_id,
            source_identifier=self.state.source_identifier,
            other_identifiers=self.data.other_identifiers,
        )
        yield from DisplayIdentifier.from_all_identifiers(all_ids)

    @property
    def thumbnail(self) -> DisplayDigitalLocation | None:
        if self.data.thumbnail is None:
            return None

        location = DisplayLocation.from_location(self.data.thumbnail)
        assert isinstance(location, DisplayDigitalLocation)
        return location

    @property
    def work_type(self) -> DisplayIdLabel | None:
        if self.data.format is None:
            return None

        return DisplayIdLabel.from_id_label(self.data.format, "Format")

    @property
    def notes(self) -> Generator[DisplayNote]:
        grouped_notes = defaultdict(list)
        for note in self.data.notes:
            grouped_notes[note.note_type.id].append(note)

        for group in grouped_notes.values():
            yield DisplayNote(
                contents=[note.contents for note in group],
                noteType=DisplayIdLabel.from_id_label(group[0].note_type, "NoteType"),
            )

    @property
    def languages(self) -> Generator[DisplayIdLabel]:
        for language in self.data.languages:
            yield DisplayIdLabel.from_id_label(language, "Language")

    @property
    def created_date(self) -> DisplayConcept | None:
        if self.data.created_date is None:
            return None

        return DisplayConcept(label=self.data.created_date.label, type="Period")

    @property
    def items(self) -> Generator[DisplayItem]:
        for item in self.data.items:
            yield DisplayItem(
                id=item.id.canonical_id,
                identifiers=list(DisplayIdentifier.from_all_identifiers(item.id)),
                title=item.title,
                note=item.note,
                locations=[
                    DisplayLocation.from_location(loc) for loc in item.locations
                ],
            )

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
        for image in self.data.image_data:
            yield DisplayId(id=image.id.canonical_id, type="Image")

    @property
    def subjects(self) -> Generator[DisplaySubject]:
        for subject in self.data.subjects:
            subject_id = None
            if subject.id is not None:
                subject_id = subject.id.canonical_id

            yield DisplaySubject(
                label=subject.label,
                id=subject_id,
                concepts=[DisplayConcept.from_concept(c) for c in subject.concepts],
            )

    @property
    def availabilities(self) -> list[DisplayIdLabel]:
        return [
            DisplayAvailability.from_availability(a) for a in self.state.availabilities
        ]

    @property
    def part_of(self) -> list[DisplayRelation]:
        # TODO: Handle series
        if len(self.hierarchy.ancestor_works) == 0:
            return []

        ancestors = DisplayRelation.from_flat_hierarchy(
            self.hierarchy.ancestor_works, 1
        )

        return [ancestors]

    @property
    def parts(self) -> list[DisplayRelation]:
        parts = [
            DisplayRelation.from_neptune_node(c.work, c.parts)
            for c in self.hierarchy.children
        ]

        return sorted(
            parts, key=lambda item: natural_sort_key(item.referenceNumber or item.title)
        )

    @property
    def contributors(self) -> Generator[DisplayContributor]:
        for contributor in self.data.contributors:
            roles = [DisplayContributionRole(label=r.label) for r in contributor.roles]
            yield DisplayContributor(
                agent=DisplayConcept.from_concept(contributor.agent),
                roles=roles,
                primary=contributor.primary,
            )

    @property
    def genres(self) -> Generator[DisplayGenre]:
        for genre in self.data.genres:
            concepts = [DisplayConcept.from_concept(c) for c in genre.concepts]
            yield DisplayGenre(concepts=concepts, label=genre.label)

    @property
    def production(self) -> Generator[DisplayProductionEvent]:
        for event in self.data.production:
            function = None
            if event.function is not None:
                function = DisplayConcept(label=event.function.label)

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
                function=function,
            )
