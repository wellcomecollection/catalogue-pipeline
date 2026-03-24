from pydantic import Field

from ingestor.extractors.images_extractor import ExtractedImage
from ingestor.transformers.work_query_transformer import QueryWorkDataTransformer
from models.pipeline.serialisable import ElasticsearchModel


class ImageFilterableValues(ElasticsearchModel):
    locations_license_id: list[str] = Field(serialization_alias="locations.license.id")
    source_contributors_agent_label: list[str] = Field(
        serialization_alias="source.contributors.agent.label"
    )
    source_contributors_agent_id: list[str] = Field(
        serialization_alias="source.contributors.agent.id"
    )
    source_contributors_agent_source_identifier: list[str] = Field(
        serialization_alias="source.contributors.agent.sourceIdentifier"
    )
    source_genres_label: list[str] = Field(serialization_alias="source.genres.label")
    source_genres_concepts_id: list[str] = Field(
        serialization_alias="source.genres.concepts.id"
    )
    source_genres_concepts_source_identifier: list[str] = Field(
        serialization_alias="source.genres.concepts.sourceIdentifier"
    )
    source_subjects_label: list[str] = Field(
        serialization_alias="source.subjects.label"
    )
    source_subjects_concepts_id: list[str] = Field(
        serialization_alias="source.subjects.concepts.id"
    )
    source_subjects_concepts_source_identifier: list[str] = Field(
        serialization_alias="source.subjects.concepts.sourceIdentifier"
    )
    source_production_dates_range_from: list[int] = Field(
        serialization_alias="source.production.dates.range.from"
    )

    @classmethod
    def from_extracted_image(cls, extracted: ExtractedImage) -> "ImageFilterableValues":
        transformer = QueryWorkDataTransformer(
            extracted.image.source.data,
            extracted.concepts,
            extracted.image.source.id.canonical_id,
        )

        return ImageFilterableValues(
            locations_license_id=[
                loc.license.id for loc in extracted.image.locations if loc.license
            ],
            source_production_dates_range_from=list(transformer.production_dates_from),
            source_contributors_agent_label=list(transformer.contributor_agent_labels),
            source_contributors_agent_id=list(transformer.contributor_ids),
            source_contributors_agent_source_identifier=list(
                transformer.contributor_identifiers
            ),
            source_genres_label=list(transformer.genre_labels),
            source_genres_concepts_id=list(transformer.genre_ids),
            source_genres_concepts_source_identifier=list(
                transformer.genre_identifiers
            ),
            source_subjects_label=list(transformer.subject_labels),
            source_subjects_concepts_id=list(transformer.subject_ids),
            source_subjects_concepts_source_identifier=list(
                transformer.subject_identifiers
            ),
        )
