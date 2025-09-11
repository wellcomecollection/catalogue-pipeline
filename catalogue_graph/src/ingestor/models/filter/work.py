from pydantic import Field

from ingestor.models.shared.serialisable import ElasticsearchModel


class WorkFilterableValues(ElasticsearchModel):
    format_id: str | None = Field(serialization_alias="format.id")
    work_type: str = Field(serialization_alias="workType")
    production_dates_range_from: list[int] = Field(
        serialization_alias="production.dates.range.from"
    )
    languages_id: list[str] = Field(serialization_alias="languages.id")
    genres_label: list[str] = Field(serialization_alias="genres.label")
    genres_concepts_id: list[str] = Field(serialization_alias="genres.concepts.id")
    genres_concepts_source_identifier: list[str] = Field(
        serialization_alias="genres.concepts.sourceIdentifier"
    )
    subjects_label: list[str] = Field(serialization_alias="subjects.label")
    subjects_concepts_id: list[str] = Field(serialization_alias="subjects.concepts.id")
    subjects_concepts_source_identifier: list[str] = Field(
        serialization_alias="subjects.concepts.sourceIdentifier"
    )
    contributors_agent_label: list[str] = Field(
        serialization_alias="contributors.agent.label"
    )
    contributors_agent_id: list[str] = Field(
        serialization_alias="contributors.agent.id"
    )
    contributors_agent_source_identifier: list[str] = Field(
        serialization_alias="contributors.agent.sourceIdentifier"
    )
    identifiers_value: list[str] = Field(serialization_alias="identifiers.value")
    items_locations_license_id: list[str] = Field(
        serialization_alias="items.locations.license.id"
    )
    items_locations_access_conditions_status_id: list[str] = Field(
        serialization_alias="items.locations.accessConditions.status.id"
    )
    items_id: list[str] = Field(serialization_alias="items.id")
    items_identifiers_value: list[str] = Field(
        serialization_alias="items.identifiers.value"
    )
    items_locations_location_type_id: list[str] = Field(
        serialization_alias="items.locations.locationType.id"
    )
    part_of_id: list[str] = Field(serialization_alias="partOf.id")
    part_of_title: list[str] = Field(serialization_alias="partOf.title")
    availabilities_id: list[str] = Field(serialization_alias="availabilities.id")
