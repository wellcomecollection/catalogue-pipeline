from pydantic import BaseModel, Field


class AggregatableField(BaseModel):
    id: str
    label: str


class WorkAggregatableValues(BaseModel):
    workTypes: list[AggregatableField] = Field(serialization_alias="workType")
    genres: list[AggregatableField] = Field(serialization_alias="genres")
    productionDates: list[AggregatableField] = Field(
        serialization_alias="production.dates"
    )
    subjects: list[AggregatableField] = Field(serialization_alias="subjects")
    languages: list[AggregatableField] = Field(serialization_alias="languages")
    contributors: list[AggregatableField] = Field(
        serialization_alias="contributors.agent"
    )
    itemLicenses: list[AggregatableField] = Field(
        serialization_alias="items.locations.license"
    )
    availabilities: list[AggregatableField] = Field(
        serialization_alias="availabilities"
    )
