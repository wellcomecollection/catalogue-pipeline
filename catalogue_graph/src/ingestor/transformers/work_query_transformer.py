from collections.abc import Generator

from ingestor.models.denormalised.work import DenormalisedWork, PhysicalLocation
from ingestor.models.neptune.query_result import WorkConcept, WorkHierarchy


class QueryWorkTransformer:
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
    def identifiers(self) -> list[str]:
        yield self.state.source_identifier.value
        for identifier in self.data.other_identifiers:
            return identifier.value

    @property
    def item_ids(self) -> Generator[str]:
        for item in self.data.items:
            if item.id.canonical_id is not None:
                yield item.id.canonical_id

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
        return [image.id.canonical_id for image in self.data.image_data]

    @property
    def image_source_identifiers(self) -> Generator[str]:
        for image in self.data.image_data:
            for identifier in image.id.get_identifiers():
                yield identifier.value

    @property
    def collection_path(self) -> str | None:
        if self.data.collection_path is None:
            return None

        return self.data.collection_path.path

    @property
    def collection_path_label(self) -> str | None:
        if self.data.collection_path is None:
            return None

        return self.data.collection_path.label

    @property
    def contributor_labels(self) -> list[str]:
        return [c.agent.label for c in self.data.contributors]
