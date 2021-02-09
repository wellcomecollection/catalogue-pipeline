package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{OldLocationType, LocationType}

@Schema(
  name = "LocationType"
)
case class DisplayLocationType(
  @Schema id: String,
  @Schema label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "LocationType"
)

object DisplayLocationType {
  def apply(locationType: OldLocationType): DisplayLocationType =
    DisplayLocationType(
      id = locationType.id,
      label = locationType.label
    )

  def apply(locationType: LocationType): DisplayLocationType =
    locationType match {
      case LocationType.ClosedStores =>
        DisplayLocationType("closed-stores", "Closed Stores")

      case LocationType.OpenShelves =>
        DisplayLocationType("open-shelves", "Open Shelves")

      case LocationType.OnExhibition =>
        DisplayLocationType("on-exhibition", "On Exhibition")

      case LocationType.IIIFImageAPI =>
        DisplayLocationType("iiif-image", "IIIF Image API")

      case LocationType.IIIFPresentationAPI =>
        DisplayLocationType("iiif-presentation", "IIIF Presentation API")

      case LocationType.OnlineResource =>
        DisplayLocationType("online-resource", "Online Resource")
    }
}
