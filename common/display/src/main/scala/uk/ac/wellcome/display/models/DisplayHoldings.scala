package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import weco.catalogue.internal_model.work.Holdings

@Schema(
  name = "Holdings",
  description = "A collection of materials owned by the library."
)
case class DisplayHoldings(
  @Schema(
    description = "Additional information about the holdings."
  ) note: Option[String],
  @Schema(
    description =
      "A list of individual issues or parts that make up the holdings."
  ) enumeration: List[String],
  @Schema(
    description = "List of locations where the holdings are stored."
  ) location: Option[DisplayLocation],
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Holdings"
)

case object DisplayHoldings {
  def apply(h: Holdings): DisplayHoldings =
    DisplayHoldings(
      note = h.note,
      enumeration = h.enumeration,
      location = h.location.map { DisplayLocation(_) }
    )
}
