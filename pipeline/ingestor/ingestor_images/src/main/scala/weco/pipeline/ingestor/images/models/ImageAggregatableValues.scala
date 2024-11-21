package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.pipeline.ingestor.common.models.{
  AggregatableIdLabel,
  AggregatableValues
}

case class ImageAggregatableValues(
  @JsonKey("locations.license") licenses: List[AggregatableIdLabel],
  @JsonKey("source.contributors.agent") contributors: List[AggregatableIdLabel],
  @JsonKey("source.genres") genres: List[AggregatableIdLabel],
  @JsonKey("source.subjects") subjects: List[AggregatableIdLabel]
)

case object ImageAggregatableValues
    extends AggregatableValues
    with ImageValues {
  def apply(image: Image[ImageState.Augmented]): ImageAggregatableValues =
    ImageAggregatableValues(
      licenses = fromParentWork(image.source)(_.data.licenseAggregatableValues),
      contributors =
        fromParentWork(image.source)(_.data.contributorAggregatableValues),
      genres = fromParentWork(image.source)(_.data.genreAggregatableValues),
      subjects = fromParentWork(image.source)(_.data.subjectAggregatableValues)
    )

}
