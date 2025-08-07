from collections.abc import Generator

from ingestor.extractors.works_extractor import ExtractedWork
from ingestor.models.denormalised.work import PhysicalLocation


class QueryWorkTransformer:
    def __init__(self, extracted: ExtractedWork):
        self.data = extracted.work.data
        self.state = extracted.work.state
        self.hierarchy = extracted.hierarchy
        self.concepts = extracted.concepts

    @property
    def identifiers(self) -> Generator[str]:
        yield self.state.source_identifier.value
        for identifier in self.data.other_identifiers:
            yield identifier.value

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
    def production_labels(self) -> Generator[str]:
        for event in self.data.production:
            for concept in event.places + event.agents + event.dates:
                yield concept.label

    @property
    def part_of_titles(self) -> Generator[str]:
        for work in self.hierarchy.ancestor_works:
            if work.properties.label is not None:
                yield work.properties.label

    @property
    def genre_labels(self) -> Generator[str]:
        for genre in self.data.genres:
            for concept in genre.concepts:
                yield concept.label

    @property
    def subject_labels(self) -> Generator[str]:
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
