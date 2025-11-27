from collections import defaultdict
from collections.abc import Generator

from ingestor.extractors.works_extractor import VisibleExtractedWork
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
from ingestor.models.display.relation import (
    DisplayRelation,
)
from models.pipeline.concept import Concept
from models.pipeline.identifier import Identified
from utils.sort import natural_sort_key

from .work_base_transformer import WorkBaseTransformer


class DisplayWorkTransformer(WorkBaseTransformer):
    def __init__(self, extracted: VisibleExtractedWork):
        super().__init__(extracted)
        self.data = extracted.work.data
        self.state = extracted.work.state
        self.hierarchy = extracted.hierarchy

    @property
    def identifiers(self) -> Generator[DisplayIdentifier]:
        all_ids = Identified(
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
        labels = set()
        for subject in self.data.subjects:
            main_concept = self.get_display_concept(subject)
            concepts = [self.get_display_concept(c) for c in subject.concepts]

            # If multiple non-composite subjects have the same standard labels, only include one of them.
            # This does not apply to composite subjects, which can have different nested concepts when their
            # standard labels match.
            if len(concepts) == 1:
                if main_concept.standard_label in labels:
                    continue
                labels.add(main_concept.standard_label)

            yield DisplaySubject(
                **main_concept.model_dump(),
                concepts=concepts,
            )

    @property
    def availabilities(self) -> list[DisplayIdLabel]:
        return [
            DisplayAvailability.from_availability(a) for a in self.state.availabilities
        ]

    @property
    def part_of(self) -> Generator[DisplayRelation]:
        if self.state.relations is not None:
            for series in self.state.relations.ancestors[::-1]:
                # If the series has the same title as one of the work's ancestors, do not include it
                if not self.hierarchy.ancestors_include_title(series.title):
                    yield DisplayRelation.from_work_ancestor(series)

        for ancestor in self.hierarchy.ancestors:
            yield DisplayRelation.from_neptune_node(ancestor.work, ancestor.parts)

    @property
    def parts(self) -> Generator[DisplayRelation]:
        sorted_children = sorted(
            self.hierarchy.children,
            key=lambda c: natural_sort_key(c.work.properties.collection_path),
        )

        for child in sorted_children:
            yield DisplayRelation.from_neptune_node(child.work, child.parts)

    def get_display_concept(self, concept: Concept) -> DisplayConcept:
        identifiers = list(DisplayIdentifier.from_all_identifiers(concept.id))
        return DisplayConcept(
            id=concept.id.canonical_id,
            label=concept.label,
            standard_label=self.get_standard_concept_label(concept),
            identifiers=None if len(identifiers) == 0 else identifiers,
            type=concept.display_type,
        )

    @property
    def contributors(self) -> Generator[DisplayContributor]:
        labels = set()

        for contributor in self.data.contributors:
            roles = [DisplayContributionRole(label=r.label) for r in contributor.roles]
            agent = self.get_display_concept(contributor.agent)

            # If multiple contributors have the same standard labels, only include one of them
            if agent.standard_label in labels:
                continue
            labels.add(agent.standard_label)

            yield DisplayContributor(
                agent=agent,
                roles=roles,
                primary=contributor.primary,
            )

    @property
    def genres(self) -> Generator[DisplayGenre]:
        for genre in self.data.genres:
            concepts = [self.get_display_concept(c) for c in genre.concepts]
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
