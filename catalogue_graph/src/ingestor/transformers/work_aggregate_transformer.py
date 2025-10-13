import re
from collections.abc import Generator

from pydantic import BaseModel

from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.models.display.availability import DisplayAvailability
from ingestor.models.display.license import DisplayLicense
from models.pipeline.identifier import Identifiers, Unidentifiable


class AggregatableField(BaseModel):
    id: str
    label: str


def get_aggregatable(
    ids: Identifiers | Unidentifiable | None, label: str
) -> AggregatableField:
    if ids is None or isinstance(ids, Unidentifiable):
        return AggregatableField(id=label, label=label)

    return AggregatableField(id=ids.canonical_id, label=label)


class AggregateWorkTransformer:
    def __init__(self, extracted: VisibleExtractedWork):
        self.data = extracted.work.data
        self.state = extracted.work.state

    @property
    def genres(self) -> Generator[AggregatableField]:
        for genre in self.data.genres:
            assert isinstance(genre.concepts[0].id, Identifiers)
            concept_id = genre.concepts[0].id.canonical_id

            yield AggregatableField(id=concept_id, label=genre.label)

    @property
    def subjects(self) -> Generator[AggregatableField]:
        for subject in self.data.subjects:
            yield get_aggregatable(subject.id, subject.normalised_label)

    @property
    def contributors(self) -> Generator[AggregatableField]:
        for c in self.data.contributors:
            yield get_aggregatable(c.agent.id, c.agent.normalised_label)

    @property
    def work_type(self) -> Generator[AggregatableField]:
        if self.data.format is None:
            return

        yield AggregatableField(**self.data.format.model_dump())

    @property
    def licenses(self) -> Generator[AggregatableField]:
        for item in self.data.items:
            for loc in item.locations:
                display_license = DisplayLicense.from_location(loc)
                if display_license is not None:
                    yield AggregatableField(**display_license.model_dump())

    @property
    def availabilities(self) -> Generator[AggregatableField]:
        for availability in self.state.availabilities:
            display_availability = DisplayAvailability.from_availability(availability)
            yield AggregatableField(**display_availability.model_dump())

    @property
    def production_dates(self) -> Generator[AggregatableField]:
        for event in self.data.production:
            for date in event.dates:
                if date.range is not None:
                    from_year_match = re.match(r"^-?\d+", date.range.from_time)
                    if not from_year_match:
                        raise ValueError(f"Invalid date format: {date.range.from_time}")

                    year = from_year_match.group()
                    yield AggregatableField(id=year, label=year)

    @property
    def languages(self) -> Generator[AggregatableField]:
        # TODO: Investigate MarcLanguageCodeList
        for language in self.data.languages:
            yield AggregatableField(**language.model_dump())
