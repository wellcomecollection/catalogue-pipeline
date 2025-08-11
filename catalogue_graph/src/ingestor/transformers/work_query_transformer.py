from collections.abc import Generator

from dateutil import parser

from ingestor.extractors.works_extractor import ExtractedWork
from ingestor.models.display.access_status import DisplayAccessStatus
from ingestor.models.shared.location import PhysicalLocation


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
            yield from item.id.get_identifier_values()

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
    def part_of_ids(self) -> Generator[str]:
        for work in self.hierarchy.ancestor_works:
            yield work.properties.id

    @property
    def genre_concept_labels(self) -> Generator[str]:
        for genre in self.data.genres:
            for concept in genre.concepts:
                yield concept.label

    @property
    def subject_concept_labels(self) -> Generator[str]:
        for subject in self.data.subjects:
            for concept in subject.concepts:
                yield concept.label

    @property
    def image_ids(self) -> list[str]:
        return [image.id.canonical_id for image in self.data.image_data]

    @property
    def image_source_identifiers(self) -> Generator[str]:
        for image in self.data.image_data:
            yield from image.id.get_identifier_values()

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
    def contributor_agent_labels(self) -> list[str]:
        return [c.agent.label for c in self.data.contributors]

    @property
    def format_id(self) -> str | None:
        if self.data.format is not None:
            return self.data.format.id

        return None

    @property
    def contributor_ids(self) -> Generator[str]:
        for contributor in self.data.contributors:
            canonical_id = contributor.agent.id.canonical_id
            if canonical_id is not None:
                yield canonical_id

    # Filter
    @property
    def production_dates_from(self) -> Generator[int]:
        for event in self.data.production:
            for date in event.dates:
                if date.range is not None:
                    # Number of milliseconds since the Unix epoch
                    yield int(parser.parse(date.range.from_time).timestamp() * 1000)

    @property
    def genre_ids(self) -> Generator[str]:
        for genre in self.data.genres:
            first_concept = genre.concepts[0]
            canonical_id = first_concept.id.canonical_id
            if canonical_id is not None:
                yield canonical_id

    @property
    def genre_identifiers(self) -> Generator[str]:
        for genre in self.data.genres:
            # TODO: Add comment
            first_concept = genre.concepts[0]
            yield from first_concept.id.get_identifier_values()

    @property
    def subject_identifiers(self) -> Generator[str]:
        for subject in self.data.subjects:
            yield from subject.id.get_identifier_values()

    @property
    def contributor_identifiers(self) -> Generator[str]:
        for contributor in self.data.contributors:
            yield from contributor.agent.id.get_identifier_values()

    @property
    def subject_ids(self) -> Generator[str]:
        for subject in self.data.subjects:
            canonical_id = subject.id.canonical_id
            if canonical_id is not None:
                yield canonical_id

    @property
    def access_condition_status_ids(self) -> Generator[str]:
        for item in self.data.items:
            for location in item.locations:
                for condition in location.access_conditions:
                    display_status = DisplayAccessStatus.from_access_condition(
                        condition
                    )
                    if display_status is not None:
                        yield display_status.id

    @property
    def license_ids(self) -> Generator[str]:
        for item in self.data.items:
            for loc in item.locations:
                if loc.license is not None:
                    yield loc.license.id

    @property
    def location_type_ids(self) -> Generator[str]:
        for item in self.data.items:
            for loc in item.locations:
                yield loc.location_type.id
