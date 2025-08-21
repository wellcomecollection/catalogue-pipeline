from pydantic import BaseModel, Field


class AggregatableField(BaseModel):
    id: str
    label: str


class WorkAggregatableValues(BaseModel):
    workType: list[AggregatableField]
    genres: list[AggregatableField]
    subjects: list[AggregatableField]
    languages: list[AggregatableField]
    productionDates: list[AggregatableField] = Field(
        serialization_alias="production.dates"
    )
    contributors: list[AggregatableField] = Field(
        serialization_alias="contributors.agent"
    )
    itemLicenses: list[AggregatableField] = Field(
        serialization_alias="items.locations.license"
    )
    availabilities: list[AggregatableField] = Field(
        serialization_alias="availabilities"
    )
