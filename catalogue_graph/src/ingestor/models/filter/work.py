from pydantic import Field

from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.transformers.work_query_transformer import QueryWorkTransformer
from models.pipeline.serialisable import ElasticsearchModel


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

    @classmethod
    def from_extracted_work(
        cls, extracted: VisibleExtractedWork
    ) -> "WorkFilterableValues":
        work = extracted.work
        transformer = QueryWorkTransformer(extracted)

        return WorkFilterableValues(
            format_id=transformer.format_id,
            work_type=work.data.work_type,
            production_dates_range_from=list(transformer.production_dates_from),
            languages_id=[i.id for i in work.data.languages],
            genres_label=[g.label for g in work.data.genres],
            genres_concepts_id=list(transformer.genre_ids),
            genres_concepts_source_identifier=list(transformer.genre_identifiers),
            subjects_label=list(transformer.subject_labels),
            subjects_concepts_id=list(transformer.subject_ids),
            subjects_concepts_source_identifier=list(transformer.subject_identifiers),
            contributors_agent_label=list(transformer.contributor_agent_labels),
            contributors_agent_id=list(transformer.contributor_ids),
            contributors_agent_source_identifier=list(
                transformer.contributor_identifiers
            ),
            identifiers_value=list(transformer.identifiers),
            items_locations_license_id=list(transformer.license_ids),
            items_locations_access_conditions_status_id=list(
                transformer.access_condition_status_ids
            ),
            items_id=list(transformer.item_ids),
            items_identifiers_value=list(transformer.item_identifiers),
            items_locations_location_type_id=list(transformer.location_type_ids),
            part_of_id=list(transformer.part_of_ids),
            part_of_title=list(transformer.part_of_titles),
            availabilities_id=[a.id for a in work.state.availabilities],
        )
