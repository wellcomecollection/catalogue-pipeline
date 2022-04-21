package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.work.Availability

case class DisplayAvailability(
  id: String,
  label: String,
  @JsonKey("type") ontologyType: String = "Availability"
)

object DisplayAvailability {
  def apply(availability: Availability): DisplayAvailability =
    DisplayAvailability(
      id = availability.id,
      label = availability.label
    )
}
