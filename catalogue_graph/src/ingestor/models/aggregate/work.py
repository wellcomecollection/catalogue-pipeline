from pydantic import BaseModel, Field

from ingestor.models.shared.serialisable import ElasticsearchModel


class AggregatableField(BaseModel):
    id: str
    label: str


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
