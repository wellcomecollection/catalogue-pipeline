from collections.abc import Generator

from ingestor.models.denormalised.work import DenormalisedWork, PhysicalLocation
from ingestor.models.display.identifier import DisplayIdentifier
from ingestor.models.neptune.query_result import WorkConcept, WorkHierarchy


class RawNeptuneWork:
    def __init__(
        self,
        work: DenormalisedWork,
        work_hierarchy: WorkHierarchy,
        work_concepts: list[WorkConcept],
    ):
        self.data = work.data
        self.state = work.state
        self.hierarchy = work_hierarchy
        self.concepts = work_concepts

    @property
    def other_identifiers(self) -> list[str]:
        return [i.value for i in self.data.otherIdentifiers]

    @property
    def item_ids(self) -> list[str]:
        return [
            item.id.canonicalId
            for item in self.data.items
            if item.id.canonicalId is not None
        ]

    @property
    def item_identifiers(self) -> Generator[str]:
        for item in self.data.items:
            for identifier in item.id.get_identifiers():
                yield identifier.value

    @property
    def item_shelfmarks(self) -> Generator[str]:
        for item in self.data.items:
            for loc in item.locations:
                # Shelfmarks are only available on physical locations
                if isinstance(loc, PhysicalLocation) and loc.shelfmark is not None:
                    yield loc.shelfmark

    @property
    def production_labels(self) -> list[str]:
        for event in self.data.production:
            for concept in event.places + event.agents + event.dates:
                yield concept.label

    @property
    def part_of_titles(self) -> list[str]:
        return [p.properties.label for p in self.hierarchy.ancestor_works]

    @property
    def genre_labels(self) -> list[str]:
        for genre in self.data.genres:
            for concept in genre.concepts:
                yield concept.label

    @property
    def subject_labels(self) -> list[str]:
        for subject in self.data.subjects:
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
