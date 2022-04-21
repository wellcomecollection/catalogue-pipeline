package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.locations.LocationType

case class DisplayLocationType(
  id: String,
  label: String,
  @JsonKey("type") ontologyType: String = "LocationType"
)

object DisplayLocationType {
  def apply(locationType: LocationType): DisplayLocationType =
    DisplayLocationType(id = locationType.id, label = locationType.label)
}
