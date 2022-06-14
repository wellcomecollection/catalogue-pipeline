package weco.pipeline.ingestor.works.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  DataState,
  IdState,
  SourceIdentifier
}
import weco.catalogue.internal_model.work.{Availability, Relations, WorkData}

case class WorkQueryableValues(
  @JsonKey("id") id: String,
  @JsonKey("identifiers.value") workIdentifiers: List[String],
  @JsonKey("images.id") imageIds: List[String],
  @JsonKey("images.identifiers.value") imageIdentifiers: List[String],
  @JsonKey("items.id") itemIds: List[String],
  @JsonKey("items.identifiers.value") itemIdentifiers: List[String],
  @JsonKey("items.locations.accessConditions.status.id") itemAccessStatusIds: List[
    String],
  @JsonKey("items.locations.license.id") itemLicenseIds: List[String],
  @JsonKey("items.locations.locationType.id") itemLocationTypeIds: List[String],
  @JsonKey("subjects.id") subjectIds: List[String],
  @JsonKey("subjects.identifiers.value") subjectIdentifiers: List[String],
  @JsonKey("subjects.label") subjectLabels: List[String],
  @JsonKey("subjects.concepts.label") subjectConceptLabels: List[String],
  @JsonKey("genres.concepts.label") genreConceptLabels: List[String],
  @JsonKey("languages.id") languageIds: List[String],
  @JsonKey("contributors.agent.label") contributorAgentLabels: List[String],
  @JsonKey("partOf.id") partOfIds: List[String],
  @JsonKey("partOf.title") partOfTitles: List[String],
  @JsonKey("availabilities.id") availabilityIds: List[String],
)

case object WorkQueryableValues {
  def apply(id: CanonicalId,
            sourceIdentifier: SourceIdentifier,
            workData: WorkData[DataState.Identified],
            relations: Relations,
            availabilities: Set[Availability]): WorkQueryableValues = {
    val locations = workData.items.flatMap(_.locations)

    WorkQueryableValues(
      id = id.underlying,
      workIdentifiers =
        (sourceIdentifier +: workData.otherIdentifiers).map(_.value),
      imageIds = workData.imageData.map(_.id).canonicalIds,
      imageIdentifiers = workData.imageData.map(_.id).sourceIdentifiers,
      itemIds = workData.items.flatMap(_.id.maybeCanonicalId).map(_.underlying),
      itemIdentifiers =
        workData.items.flatMap(_.id.allSourceIdentifiers).map(_.value),
      itemAccessStatusIds =
        locations.flatMap(_.accessConditions).flatMap(_.status).map(_.id),
      itemLicenseIds = locations.flatMap(_.license).map(_.id),
      itemLocationTypeIds = locations.map(_.locationType.id),
      subjectIds = workData.subjects.map(_.id).canonicalIds,
      subjectIdentifiers = workData.subjects.map(_.id).sourceIdentifiers,
      subjectLabels = workData.subjects.map(_.label),
      subjectConceptLabels = workData.subjects.flatMap(_.concepts).map(_.label),
      genreConceptLabels = workData.genres.flatMap(_.concepts).map(_.label),
      languageIds = workData.languages.map(_.id),
      contributorAgentLabels = workData.contributors.map(_.agent.label),
      partOfIds = relations.ancestors.flatMap(_.id).map(_.underlying),
      partOfTitles = relations.ancestors.flatMap(_.title),
      availabilityIds = availabilities.map(_.id).toList
    )
  }

  implicit class IdStateOps(ids: Seq[IdState.Minted]) {
    def canonicalIds: List[String] =
      ids.flatMap(_.maybeCanonicalId).map(_.underlying).toList

    def sourceIdentifiers: List[String] =
      ids
        .collect {
          case IdState.Identified(_, sourceIdentifier, otherIdentifiers) =>
            sourceIdentifier +: otherIdentifiers
        }
        .flatten
        .map(_.value)
        .toList
  }
}
