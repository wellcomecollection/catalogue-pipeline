package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.{CanonicalId, SourceIdentifier}
import weco.catalogue.internal_model.image.{
  Image,
  ImageSource,
  ImageState,
  InferredData,
  ParentWork
}
import weco.catalogue.internal_model.locations.DigitalLocation
import weco.catalogue.internal_model.work.Relations
import weco.pipeline.ingestor.common.models.WorkQueryableValues

case class ImageQueryableValues(
  @JsonKey("id") id: String,
  @JsonKey("sourceIdentifier.value") sourceIdentifierValue: String,
  @JsonKey("locations.license.id") locationsLicenseId: List[String],
  @JsonKey("source") source: WorkQueryableValues
)

case object ImageQueryableValues extends ImageValues {
  def apply(
    image: Image[ImageState.Augmented]
  ): ImageQueryableValues =
    new ImageQueryableValues(
      id = image.state.canonicalId.underlying,
      sourceIdentifierValue = image.state.sourceIdentifier.value,
      locationsLicenseId = image.locations.flatMap(_.license).map(_.id),
      source = fromParentWork(image.source) {
        p =>
          WorkQueryableValues(
            canonicalId = p.id.canonicalId,
            sourceIdentifier = p.id.sourceIdentifier,
            data = p.data
          )
      }
    )
}
