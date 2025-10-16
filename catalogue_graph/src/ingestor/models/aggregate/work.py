from pydantic import Field

from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.transformers.work_aggregate_transformer import (
    AggregatableField,
    AggregateWorkTransformer,
)
from models.pipeline.serialisable import ElasticsearchModel


class WorkAggregatableValues(ElasticsearchModel):
    work_type: list[AggregatableField]
    genres: list[AggregatableField]
    subjects: list[AggregatableField]
    languages: list[AggregatableField]
    production_dates: list[AggregatableField] = Field(
        serialization_alias="production.dates"
    )
    contributors: list[AggregatableField] = Field(
        serialization_alias="contributors.agent"
    )
    item_licenses: list[AggregatableField] = Field(
        serialization_alias="items.locations.license"
    )
    availabilities: list[AggregatableField] = Field(
        serialization_alias="availabilities"
    )

    @classmethod
    def from_extracted_work(
        cls, extracted: VisibleExtractedWork
    ) -> "WorkAggregatableValues":
        transformer = AggregateWorkTransformer(extracted)
        return WorkAggregatableValues(
            work_type=list(transformer.work_type),
            genres=list(transformer.genres),
            production_dates=list(transformer.production_dates),
            subjects=list(transformer.subjects),
            languages=list(transformer.languages),
            contributors=list(transformer.contributors),
            item_licenses=list(transformer.licenses),
            availabilities=list(transformer.availabilities),
        )
