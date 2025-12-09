package weco.pipeline.ingestor.common.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  DataState,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Relations, WorkData}

case class WorkQueryableValues(
  @JsonKey("collectionPath.label") collectionPathLabel: Option[String],
  @JsonKey("collectionPath.path") collectionPathPath: Option[String],
  @JsonKey("alternativeTitles") alternativeTitles: List[String],
  @JsonKey("contributors.agent.label") contributorsAgentLabel: List[String],
  @JsonKey("description") description: Option[String],
  @JsonKey("edition") edition: Option[String],
  @JsonKey("genres.concepts.label") genresConceptsLabel: List[String],
  @JsonKey("id") id: String,
  @JsonKey("sourceIdentifier.value") sourceIdentifierValue: String,
  @JsonKey("identifiers.value") identifiersValue: List[String],
  @JsonKey("images.id") imagesId: List[String],
  @JsonKey("images.identifiers.value") imagesIdentifiersValue: List[String],
  @JsonKey("items.id") itemsId: List[String],
  @JsonKey("items.identifiers.value") itemsIdentifiersValue: List[String],
  @JsonKey("items.shelfmark.value") itemsShelfmarksValue: List[String],
  @JsonKey("languages.label") languagesLabel: List[String],
  @JsonKey("lettering") lettering: Option[String],
  @JsonKey("notes.contents") notesContents: List[String],
  @JsonKey("partOf.title") partOfTitle: List[String],
  @JsonKey("physicalDescription") physicalDescription: Option[String],
  @JsonKey("production.label") productionLabel: List[String],
  @JsonKey("referenceNumber") referenceNumber: Option[String],
  @JsonKey("subjects.concepts.label") subjectsConceptsLabel: List[String],
  @JsonKey("title") title: Option[String]
)

case object WorkQueryableValues {
  import ValueTransforms._

  def apply(
    canonicalId: CanonicalId,
    sourceIdentifier: SourceIdentifier,
    data: WorkData[DataState.Identified],
    relations: Relations = Relations.none
  ): WorkQueryableValues =
    new WorkQueryableValues(
      collectionPathLabel = data.collectionPath.flatMap(_.label),
      collectionPathPath = data.collectionPath.map(_.path),
      alternativeTitles = data.alternativeTitles,
      contributorsAgentLabel =
        data.contributors.map(_.agent.label).map(queryableLabel),
      description = data.description,
      edition = data.edition,
      genresConceptsLabel =
        data.genres.flatMap(_.concepts).map(_.label).map(queryableLabel),
      id = canonicalId.underlying,
      sourceIdentifierValue = sourceIdentifier.value,
      identifiersValue =
        (sourceIdentifier +: data.otherIdentifiers).map(_.value),
      imagesId = data.imageData.map(_.id).canonicalIds,
      imagesIdentifiersValue = data.imageData.map(_.id).sourceIdentifiers,
      itemsId = data.items.map(_.id).canonicalIds,
      itemsIdentifiersValue =
        data.items.flatMap(_.id.allSourceIdentifiers).map(_.value),
      itemsShelfmarksValue = data.items
        .flatMap(_.locations)
        .flatMap(locationShelfmark),
      languagesLabel = data.languages.map(_.label),
      lettering = data.lettering,
      notesContents = data.notes.map(_.contents),
      partOfTitle = relations.ancestors.flatMap(_.title),
      physicalDescription = data.physicalDescription,
      productionLabel = data.production.flatMap(
        p =>
          p.places.map(_.label) ++ p.agents.map(_.label) ++ p.dates.map(_.label)
      ),
      referenceNumber = data.referenceNumber.map(_.underlying),
      subjectsConceptsLabel =
        data.subjects.flatMap(_.concepts).map(_.label).map(queryableLabel),
      title = data.title
    )
}
