package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.{CanonicalId, SourceIdentifier}
import weco.catalogue.internal_model.image.{
  ImageSource,
  InferredData,
  ParentWork
}
import weco.catalogue.internal_model.work.Relations
import weco.pipeline.ingestor.common.models.WorkQueryableValues

case class ImageQueryableValues(
  @JsonKey("id") id: String,
  @JsonKey("sourceIdentifier.value") sourceIdentifier: String,
  @JsonKey("inferredData") inferredData: InferredData,
  @JsonKey("source") source: WorkQueryableValues,
)

case object ImageQueryableValues {
  def apply(id: CanonicalId,
            sourceIdentifier: SourceIdentifier,
            inferredData: InferredData,
            source: ImageSource): ImageQueryableValues =
    source match {
      case ParentWork(workId, workData, _) =>
        ImageQueryableValues(
          id = id.underlying,
          sourceIdentifier = sourceIdentifier.value,
          inferredData = inferredData,
          source = WorkQueryableValues(
            id = workId.canonicalId,
            sourceIdentifier = workId.sourceIdentifier,
            workData = workData,
            relations = Relations.none,
            availabilities = Set()
          )
        )
    }
}
