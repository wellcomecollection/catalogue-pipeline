package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.{ImageSource, ParentWorks}
import weco.catalogue.internal_model.work.Subject

case class ImageQueryableValues(
  @JsonKey("source.subjects.id") sourceSubjectIds: Seq[String],
  @JsonKey("source.subjects.label") sourceSubjectLabels: Seq[String]
)

case object ImageQueryableValues {
  def apply(source: ImageSource): ImageQueryableValues =
    source match {
      case ParentWorks(canonicalWork, Some(redirectedWork)) =>
        val subjects: Seq[Subject[IdState.Minted]] = canonicalWork.data.subjects ++ redirectedWork.data.subjects
        ImageQueryableValues(subjects)

      case ParentWorks(canonicalWork, _) =>
        ImageQueryableValues(canonicalWork.data.subjects)
    }

  private def apply(subjects: Seq[Subject[IdState.Minted]]): ImageQueryableValues =
    ImageQueryableValues(
      sourceSubjectIds = subjects.flatMap(_.id.maybeCanonicalId).map(_.underlying),
      sourceSubjectLabels = subjects.map(_.label),
    )
}
