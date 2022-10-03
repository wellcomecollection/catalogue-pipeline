package weco.pipeline.ingestor.common.models

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
  @JsonKey("format.id") formatId: Option[String],
  @JsonKey("workType") workType: String,
  @JsonKey("identifiers.value") workIdentifiers: List[String],
  @JsonKey("title") title: Option[String],
  @JsonKey("alternativeTitles") alternativeTitles: List[String],
  @JsonKey("description") description: Option[String],
  @JsonKey("physicalDescription") physicalDescription: Option[String],
  @JsonKey("edition") edition: Option[String],
  @JsonKey("notes.contents") noteContents: List[String],
  @JsonKey("lettering") lettering: Option[String],
  @JsonKey("images.id") imageIds: List[String],
  @JsonKey("images.identifiers.value") imageIdentifiers: List[String],
  @JsonKey("items.id") itemIds: List[String],
  @JsonKey("items.identifiers.value") itemIdentifiers: List[String],
  @JsonKey("items.locations.accessConditions.status.id") itemAccessStatusIds: List[
    String],
  @JsonKey("items.locations.license.id") itemLicenseIds: List[String],
  @JsonKey("items.locations.locationType.id") itemLocationTypeIds: List[String],
  @JsonKey("subjects.id") subjectIds: List[String],
  @JsonKey("subjects.label") subjectLabels: List[String],
  @JsonKey("subjects.concepts.label") subjectConceptLabels: List[String],
  @JsonKey("genres.label") genreLabels: List[String],
  @JsonKey("genres.concepts.label") genreConceptLabels: List[String],
  @JsonKey("languages.id") languageIds: List[String],
  @JsonKey("languages.label") languageLabels: List[String],
  @JsonKey("contributors.agent.id") contributorAgentIds: List[String],
  @JsonKey("contributors.agent.label") contributorAgentLabels: List[String],
  @JsonKey("production.label") productionLabels: List[String],
  @JsonKey("production.dates.range.from") productionDatesRangeFrom: List[Long],
  @JsonKey("partOf.id") partOfIds: List[String],
  @JsonKey("partOf.title") partOfTitles: List[String],
  @JsonKey("availabilities.id") availabilityIds: List[String],
  @JsonKey("collectionPath.label") collectionPathLabel: Option[String],
  @JsonKey("collectionPath.path") collectionPathPath: Option[String],
  @JsonKey("referenceNumber") referenceNumber: Option[String],
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
      formatId = workData.format.map(_.id),
      workType = workData.workType.toString,
      workIdentifiers =
        (sourceIdentifier +: workData.otherIdentifiers).map(_.value),
      title = workData.title,
      alternativeTitles = workData.alternativeTitles,
      description = workData.description,
      physicalDescription = workData.physicalDescription,
      edition = workData.edition,
      noteContents = workData.notes.map(_.contents),
      lettering = workData.lettering,
      imageIds = workData.imageData.map(_.id).canonicalIds,
      imageIdentifiers = workData.imageData.map(_.id).sourceIdentifiers,
      itemIds = workData.items.map(_.id).canonicalIds,
      itemIdentifiers =
        workData.items.flatMap(_.id.allSourceIdentifiers).map(_.value),
      itemAccessStatusIds =
        locations.flatMap(_.accessConditions).flatMap(_.status).map(_.id),
      itemLicenseIds = locations.flatMap(_.license).map(_.id),
      itemLocationTypeIds = locations.map(_.locationType.id),
      subjectIds = workData.subjects.map(_.id).canonicalIds,
      subjectLabels = workData.subjects.map(_.label),
      subjectConceptLabels = workData.subjects.flatMap(_.concepts).map(_.label),
      genreLabels = workData.genres.map(_.label),
      genreConceptLabels = workData.genres.flatMap(_.concepts).map(_.label),
      languageIds = workData.languages.map(_.id),
      languageLabels = workData.languages.map(_.label),
      contributorAgentIds = workData.contributors.map(_.agent.id).canonicalIds,
      contributorAgentLabels = workData.contributors.map(_.agent.label),
      productionLabels = workData.production.flatMap(p =>
        p.places.map(_.label) ++ p.agents.map(_.label) ++ p.dates.map(_.label)),
      productionDatesRangeFrom = workData.production
        .flatMap(_.dates)
        .flatMap(_.range)
        .map(
          // Note: the Elasticsearch date field type wants milliseconds since
          // the epoch.
          // See https://www.elastic.co/guide/en/elasticsearch/reference/current/date.html
          _.from.toEpochMilli),
      partOfIds = relations.ancestors.flatMap(_.id).map(_.underlying),
      partOfTitles = relations.ancestors.flatMap(_.title),
      availabilityIds = availabilities.map(_.id).toList,
      collectionPathLabel = workData.collectionPath.flatMap(_.label),
      collectionPathPath = workData.collectionPath.map(_.path),
      referenceNumber = workData.referenceNumber.map(_.underlying)
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
