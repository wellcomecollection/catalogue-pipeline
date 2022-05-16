package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.image.{ImageSource, ParentWorks}
import weco.catalogue.internal_model.work.WorkData
import weco.pipeline.ingestor.common.models.AggregatableValues

case class ImageAggregatableValues(
  @JsonKey("locations.license") licenses: List[String],
  @JsonKey("source.contributors.agent.label") contributors: List[String],
  @JsonKey("source.genres.label") genres: List[String]
)

case object ImageAggregatableValues extends AggregatableValues {
  def apply(source: ImageSource): ImageAggregatableValues =
    source match {
      case ParentWorks(canonicalWork, _) => fromWorkData(canonicalWork.data)
    }

  private def fromWorkData(workData: WorkData[DataState.Identified]): ImageAggregatableValues =
    ImageAggregatableValues(
      licenses = workData.licenseAggregatableValues,
      contributors = workData.contributorAggregatableValues,
      genres = workData.genreAggregatableValues
    )
}
