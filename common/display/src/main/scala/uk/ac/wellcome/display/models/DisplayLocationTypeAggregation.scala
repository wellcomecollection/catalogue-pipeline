package uk.ac.wellcome.display.models

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
  name = "LocationTypeAggregation",
  description = "A location that provides access to an item",
)
case class DisplayLocationTypeAggregation(label: String, `type`: String)
object DisplayLocationTypeAggregation {
  def apply(
    locationTypeQuery: LocationTypeQuery): DisplayLocationTypeAggregation =
    locationTypeQuery match {
      case LocationTypeQuery.DigitalLocation =>
        DisplayLocationTypeAggregation("Online", "DigitalLocation")
      case LocationTypeQuery.PhysicalLocation =>
        DisplayLocationTypeAggregation("In the library", "PhysicalLocation")
    }
}
