package weco.pipeline.ingestor.images.models

import io.circe.generic.extras.JsonKey
import io.circe.syntax._
import weco.catalogue.display_model.Implicits._
import weco.catalogue.display_model.locations.DisplayLicense
import weco.catalogue.display_model.work.{DisplayAbstractAgent, DisplayGenre}
import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.image.{ImageSource, ParentWorks}
import weco.catalogue.internal_model.work.WorkData

case class ImageAggregatableValues(
  @JsonKey("locations.license") licenses: List[String],
  @JsonKey("source.contributors.agent.label") contributors: List[String],
  @JsonKey("source.genres.label") genres: List[String]
)

case object ImageAggregatableValues {
  def apply(source: ImageSource): ImageAggregatableValues =
    source match {
      case ParentWorks(canonicalWork, _) => fromWorkData(canonicalWork.data)
    }

  private def fromWorkData(
    workData: WorkData[DataState.Identified]
  ): ImageAggregatableValues = {
    val licenses: List[String] =
      workData.items
        .flatMap(_.locations)
        .flatMap(_.license)
        .map(DisplayLicense(_).asJson.deepDropNullValues)
        .map(_.noSpaces)

    val contributors: List[String] =
      workData.contributors
        .map(_.agent)
        .map(DisplayAbstractAgent(_, includesIdentifiers = false).asJson.deepDropNullValues)
        .map(json => json.mapObject(_.remove("roles")))
        .map(_.noSpaces)

    val genres: List[String] =
      workData.genres
        .map(
          DisplayGenre(_, includesIdentifiers = false).asJson.deepDropNullValues)
        .map(_.noSpaces)

    ImageAggregatableValues(licenses, contributors, genres)
  }
}
