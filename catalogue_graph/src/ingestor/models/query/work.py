from pydantic import BaseModel, Field


class QueryWork(BaseModel):
    id: str
    title: str | None
    referenceNumber: str | None
    physicalDescription: str | None
    lettering: str | None
    edition: str | None
    description: str | None
    alternativeTitles: list[str]
    languagesLabel: list[str] = Field(serialization_alias="languages.label")
    sourceIdentifierValue: str = Field(serialization_alias="sourceIdentifier.value")
    identifiersValue: list[str] = Field(serialization_alias="identifiers.value")
    imagesId: list[str] = Field(serialization_alias="images.id")
    imagesIdentifiersValue: list[str] = Field(
        serialization_alias="images.identifiers.value"
    )
    itemsIdentifiersValue: list[str] = Field(
        serialization_alias="items.identifiers.value"
    )
    itemsId: list[str] = Field(serialization_alias="items.id")
    itemsShelfmarksValue: list[str] = Field(serialization_alias="items.shelfmark.value")
    notesContents: list[str] = Field(serialization_alias="notes.contents")
    partOfTitle: list[str] = Field(serialization_alias="partOf.title")
    productionLabel: list[str] = Field(serialization_alias="production.label")
    subjectsConceptsLabel: list[str] = Field(
        serialization_alias="subjects.concepts.label"
    )
    contributorsAgentLabel: list[str] = Field(
        serialization_alias="contributors.agent.label"
    )
    genresConceptsLabel: list[str] = Field(serialization_alias="genres.concepts.label")
    collectionPathLabel: str | None = Field(serialization_alias="collectionPath.label")
    collectionPathPath: str | None = Field(serialization_alias="collectionPath.path")
