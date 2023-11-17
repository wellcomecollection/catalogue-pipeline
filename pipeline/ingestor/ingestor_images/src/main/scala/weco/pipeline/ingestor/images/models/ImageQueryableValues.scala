package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.image.{Image, ImageSource, ImageState}
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
      source = sourceQueryableValues(image.source)
    )

  private def sourceQueryableValues(imageSource: ImageSource): WorkQueryableValues =
    fromParentWork(imageSource) {
      p =>
        WorkQueryableValues(
          canonicalId = p.id.canonicalId,
          sourceIdentifier = p.id.sourceIdentifier,
          data = p.data
        )
    }
}
