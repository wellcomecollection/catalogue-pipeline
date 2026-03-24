from pydantic import Field

from ingestor.extractors.works_extractor import VisibleExtractedWork
from ingestor.models.neptune.query_result import ExtractedConcept
from ingestor.transformers.work_query_transformer import QueryWorkDataTransformer
from models.pipeline.identifier import (
    SourceIdentifier,
)
from models.pipeline.serialisable import ElasticsearchModel
from models.pipeline.work_data import WorkData


class QueryWork(ElasticsearchModel):
    id: str
    title: str | None
    reference_number: str | None
    physical_description: str | None
    lettering: str | None
    edition: str | None
    description: str | None
    alternative_titles: list[str]
    languages_label: list[str] = Field(serialization_alias="languages.label")
    source_identifier_value: str = Field(serialization_alias="sourceIdentifier.value")
    identifiers_value: list[str] = Field(serialization_alias="identifiers.value")
    images_id: list[str] = Field(serialization_alias="images.id")
    images_identifiers_value: list[str] = Field(
        serialization_alias="images.identifiers.value"
    )
    items_identifiers_value: list[str] = Field(
        serialization_alias="items.identifiers.value"
    )
    items_id: list[str] = Field(serialization_alias="items.id")
    items_shelfmarks_value: list[str] = Field(
        serialization_alias="items.shelfmark.value"
    )
    notes_contents: list[str] = Field(serialization_alias="notes.contents")
    part_of_title: list[str] = Field(serialization_alias="partOf.title")
    production_label: list[str] = Field(serialization_alias="production.label")
    subjects_concepts_label: list[str] = Field(
        serialization_alias="subjects.concepts.label"
    )
    contributors_agent_label: list[str] = Field(
        serialization_alias="contributors.agent.label"
    )
    genres_concepts_label: list[str] = Field(
        serialization_alias="genres.concepts.label"
    )
    collection_path_label: str | None = Field(
        serialization_alias="collectionPath.label"
    )
    collection_path_path: str | None = Field(serialization_alias="collectionPath.path")

    @classmethod
    def from_work_data(
        cls,
        data: WorkData,
        concepts: list[ExtractedConcept],
        work_id: str,
        source_identifier: SourceIdentifier,
    ) -> "QueryWork":
        transformer = QueryWorkDataTransformer(data, concepts, work_id)

        return QueryWork(
            id=work_id,
            collection_path_label=transformer.collection_path_label,
            collection_path_path=transformer.collection_path,
            alternative_titles=data.alternative_titles,
            contributors_agent_label=transformer.contributor_agent_labels,
            genres_concepts_label=list(transformer.genre_concept_labels),
            subjects_concepts_label=list(transformer.subject_concept_labels),
            description=data.description,
            edition=data.edition,
            source_identifier_value=source_identifier.value,
            identifiers_value=list(transformer.identifiers),
            images_id=transformer.image_ids,
            images_identifiers_value=list(transformer.image_source_identifiers),
            items_id=list(transformer.item_ids),
            items_identifiers_value=list(transformer.item_identifiers),
            items_shelfmarks_value=list(transformer.item_shelfmarks),
            languages_label=[i.label for i in data.languages],
            lettering=data.lettering,
            notes_contents=[n.contents for n in data.notes],
            production_label=list(transformer.production_labels),
            part_of_title=list(transformer.part_of_titles),
            physical_description=data.physical_description,
            reference_number=data.reference_number,
            title=data.title,
        )

    @classmethod
    def from_extracted_work(cls, extracted: VisibleExtractedWork) -> "QueryWork":
        return cls.from_work_data(
            extracted.work.data,
            extracted.concepts,
            extracted.work.state.canonical_id,
            extracted.work.state.source_identifier,
        )
