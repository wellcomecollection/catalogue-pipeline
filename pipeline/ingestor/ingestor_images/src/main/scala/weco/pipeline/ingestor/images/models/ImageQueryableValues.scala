package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.image.{ImageSource, ParentWork}
import weco.catalogue.internal_model.work.Relations
import weco.pipeline.ingestor.common.models.WorkQueryableValues

case class ImageQueryableValues(
  @JsonKey("source") source: WorkQueryableValues,
)

case object ImageQueryableValues {
  def apply(source: ImageSource): ImageQueryableValues =
    source match {
      case ParentWork(id, workData, _) =>
        ImageQueryableValues(
          source = WorkQueryableValues(
            id = id.canonicalId,
            sourceIdentifier = id.sourceIdentifier,
            workData = workData,
            relations = Relations.none,
            availabilities = Set()
          )
        )
    }
}
