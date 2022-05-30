package weco.pipeline.ingestor.works.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.{DataState, IdState}
import weco.catalogue.internal_model.work.WorkData

case class WorkQueryableValues(
  @JsonKey("subjects.id") subjectIds: List[String],
  @JsonKey("subjects.identifiers.value") subjectIdentifiers: List[String],
  @JsonKey("subjects.label") subjectLabels: List[String]
)

case object WorkQueryableValues {
  def apply(workData: WorkData[DataState.Identified]): WorkQueryableValues =
    WorkQueryableValues(
      subjectIds = workData.subjects.map(_.id).canonicalIds,
      subjectIdentifiers = workData.subjects.map(_.id).sourceIdentifiers,
      subjectLabels = workData.subjects.map(_.label)
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
