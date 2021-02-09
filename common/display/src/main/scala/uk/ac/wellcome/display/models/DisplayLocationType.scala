package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{LocationType, NewLocationType}

@Schema(
  name = "LocationType"
)
case class DisplayLocationType(
  @Schema id: String,
  @Schema label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "LocationType"
)

object DisplayLocationType {
  def apply(locationType: LocationType): DisplayLocationType =
    DisplayLocationType(
      id = locationType.id,
      label = locationType.label
    )

  def apply(locationType: NewLocationType): DisplayLocationType =
    locationType match {
      case NewLocationType.ClosedStores =>
        DisplayLocationType("closed-stores", "Closed Stores")

      case NewLocationType.OpenShelves =>
        DisplayLocationType("open-shelves", "Open Shelves")

      case NewLocationType.OnExhibition =>
        DisplayLocationType("on-exhibition", "On Exhibition")

      case NewLocationType.IIIFImageAPI =>
        DisplayLocationType("iiif-image", "IIIF Image API")

      case NewLocationType.IIIFPresentationAPI =>
        DisplayLocationType("iiif-presentation", "IIIF Presentation API")

      case NewLocationType.OnlineResource =>
        DisplayLocationType("online-resource", "Online Resource")
    }
}
