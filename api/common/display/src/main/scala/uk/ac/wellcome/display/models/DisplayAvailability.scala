package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import weco.catalogue.internal_model.work.Availability

@Schema(
  name = "Availability"
)
case class DisplayAvailability(
  @Schema id: String,
  @Schema label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Availability"
)

object DisplayAvailability {
  def apply(availability: Availability): DisplayAvailability =
    DisplayAvailability(
      id = availability.id,
      label = availability.label
    )
}
