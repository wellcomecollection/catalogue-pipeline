from pydantic import Field

from ingestor.models.shared.serialisable import ElasticsearchModel


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
