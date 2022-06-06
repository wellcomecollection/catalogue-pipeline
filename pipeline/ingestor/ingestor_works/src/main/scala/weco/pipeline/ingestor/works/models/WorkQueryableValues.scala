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
  @JsonKey("subjects.id") subjectIds: List[String],
  @JsonKey("subjects.identifiers.value") subjectIdentifiers: List[String],
  @JsonKey("subjects.label") subjectLabels: List[String],
  @JsonKey("partOf.id") partOfIds: List[String],
  @JsonKey("partOf.title") partOfTitles: List[String],
  @JsonKey("availabilities.id") availabilityIds: List[String],
)

case object WorkQueryableValues {
  def apply(id: CanonicalId,
            sourceIdentifier: SourceIdentifier,
            workData: WorkData[DataState.Identified],
            relations: Relations,
            availabilities: Set[Availability]): WorkQueryableValues =
    WorkQueryableValues(
      id = id.underlying,
      workIdentifiers =
        (sourceIdentifier +: workData.otherIdentifiers).map(_.value),
      subjectIds = workData.subjects.map(_.id).canonicalIds,
      subjectIdentifiers = workData.subjects.map(_.id).sourceIdentifiers,
      subjectLabels = workData.subjects.map(_.label),
      partOfIds = relations.ancestors.flatMap(_.id).map(_.underlying),
      partOfTitles = relations.ancestors.flatMap(_.title),
      availabilityIds = availabilities.map(_.id).toList
    )

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
