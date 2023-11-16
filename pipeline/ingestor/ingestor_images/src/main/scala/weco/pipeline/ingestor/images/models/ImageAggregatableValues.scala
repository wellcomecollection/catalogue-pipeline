package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.image.{
  Image,
  ImageSource,
  ImageState,
  ParentWork
}
import weco.catalogue.internal_model.work.WorkData
import weco.pipeline.ingestor.common.models.AggregatableValues

case class ImageAggregatableValues(
  @JsonKey("locations.license") licenses: List[String],
  @JsonKey("source.contributors.agent.label") contributors: List[String],
  @JsonKey("source.genres.label") genres: List[String],
  @JsonKey("source.subjects.label") subjects: List[String]
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
      subjects =
        fromParentWork(image.source)(_.data.subjectLabelAggregatableValues)
    )

}
