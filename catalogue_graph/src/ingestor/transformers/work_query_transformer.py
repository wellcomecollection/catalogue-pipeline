import re
from collections.abc import Generator, Iterable

import structlog
from dateutil import parser

from ingestor.extractors.works.base_works_extractor import VisibleExtractedWork
from ingestor.models.display.access_method import DisplayAccessMethod
from ingestor.models.display.access_status import DisplayAccessStatus
from models.pipeline.archive_category import ArchiveCategory
from models.pipeline.location import DigitalLocation, PhysicalLocation

from .work_base_transformer import WorkBaseTransformer

logger = structlog.get_logger(__name__)

# The Scala pipeline uses the date `-9999-01-01T00:00:00Z` as 'negative infinity'. The Python standard library doesn't
# support dates with negative years, and so we hardcode the corresponding Unix timestamp here instead of installing
# an external library to calculate it.
NEGATIVE_INFINITY_DATE = "-9999-01-01T00:00:00Z"
NEGATIVE_INFINITY_UNIX_TIMESTAMP = -377705116800000


def get_unique(items: Iterable[str]) -> list[str]:
    """Deduplicate items in the given iterable white preserving order"""
    return list(dict.fromkeys(items))


class QueryWorkTransformer(WorkBaseTransformer):
    def __init__(self, extracted: VisibleExtractedWork):
        super().__init__(extracted)
        self.concepts = extracted.concepts

    @property
    def identifiers(self) -> Generator[str]:
        yield self.state.canonical_id
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
        if self.state.relations is not None:
            for series_item in self.state.relations.ancestors:
                if not self.hierarchy.ancestors_include_title(series_item.title):
                    yield series_item.title

        for collection_item in self.hierarchy.ancestors[::-1]:
            if collection_item.work.properties.label is not None:
                yield collection_item.work.properties.label

    @property
    def part_of_ids(self) -> Generator[str]:
        for item in self.hierarchy.ancestors[::-1]:
            yield item.work.properties.id

    @property
    def genre_concept_labels(self) -> list[str]:
        items = []
        for genre in self.data.genres:
            for concept in genre.concepts:
                items.append(self.get_standard_concept_label(concept))

        return get_unique(items)

    @property
    def subject_concept_labels(self) -> list[str]:
        items = []
        for subject in self.data.subjects:
            for concept in subject.concepts:
                items.append(self.get_standard_concept_label(concept))

        return get_unique(items)

    @property
    def image_ids(self) -> list[str]:
        return [image.id.canonical_id for image in self.data.image_data]

    @property
    def image_source_identifiers(self) -> Generator[str]:
        for image in self.data.image_data:
            yield from image.id.get_identifier_values()

    @property
    def collection_path_sort(self) -> str | None:
        path = self.collection_path_path
        if path is None:
            return None

        # Pad all numeric segments with zeroes so that collection paths sort naturally.
        # For example, 'A/10/B' < 'A/9/B' (incorrect), but 'A/000010/B' > 'A/000009/B' (correct).
        # The `natural_sort_key` function (used in DisplayWorkTransformer) exists to support
        # the same sorting behaviour.
        return re.sub(r"\d+", lambda m: m.group().zfill(10), path)

    @property
    def collection_path_label(self) -> str | None:
        if self.data.collection_path is None:
            return None

        return self.data.collection_path.label

    @property
    def archive_category_id(self) -> str | None:
        result = ArchiveCategory.from_collection_path(self.data.collection_path)
        return result.id if result is not None else None

    @property
    def collection_root_id(self) -> str | None:
        if self.hierarchy.ancestors:
            return self.hierarchy.ancestors[-1].work.properties.id
        if self.is_collection_root:
            return self.state.canonical_id
        return None

    @property
    def collection_root_title(self) -> str | None:
        if self.hierarchy.ancestors:
            return self.hierarchy.ancestors[-1].work.properties.label
        if self.is_collection_root:
            return self.data.title
        return None

    @property
    def subject_labels(self) -> list[str]:
        return get_unique(
            self.get_standard_concept_label(s) for s in self.data.subjects
        )

    @property
    def contributor_agent_labels(self) -> list[str]:
        return get_unique(
            self.get_standard_concept_label(c.agent) for c in self.data.contributors
        )

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
                    if date.range.from_time == NEGATIVE_INFINITY_DATE:
                        yield NEGATIVE_INFINITY_UNIX_TIMESTAMP
                    else:
                        try:
                            # Number of milliseconds since the Unix epoch
                            yield int(
                                parser.parse(date.range.from_time).timestamp() * 1000
                            )
                        except parser.ParserError:
                            logger.warning(
                                "Could not parse production date",
                                work_id=self.state.canonical_id,
                            )

    @property
    def genre_ids(self) -> Generator[str]:
        for genre in self.data.genres:
            first_concept = genre.concepts[0]
            canonical_id = first_concept.id.canonical_id
            if canonical_id is not None:
                yield canonical_id

    @property
    def genre_labels(self) -> Generator[str]:
        for genre in self.data.genres:
            yield self.get_standard_concept_label(genre.concepts[0])

    @property
    def genre_identifiers(self) -> Generator[str]:
        for genre in self.data.genres:
            # Only the first concept counts, the others include things like places and periods that help
            # a reader understand more about the genre of a given item, but do not contribute meaningfully
            # to a filter, so are excluded from the query section.
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
    def access_condition_method_ids(self) -> Generator[str]:
        for item in self.data.items:
            for location in item.locations:
                for condition in location.access_conditions:
                    yield DisplayAccessMethod.from_access_condition(condition).id

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

    @property
    def items_locations_created_date(self) -> Generator[str]:
        for item in self.data.items:
            for loc in item.locations:
                # items's createdDate are only available on digital locations
                if isinstance(loc, DigitalLocation) and loc.created_date is not None:
                    yield loc.created_date
