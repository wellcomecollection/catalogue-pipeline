package uk.ac.wellcome.display.models

import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.LocationTypeAggregation

@Schema(
  name = "LocationTypeAggregation",
  description = "A location that provides access to an item",
)
case class DisplayLocationTypeAggregation(label: String, `type`: String)
object DisplayLocationTypeAggregation {
  def apply(locationAggregation: LocationTypeAggregation)
    : DisplayLocationTypeAggregation =
    locationAggregation match {
      case LocationTypeAggregation.Digital =>
        DisplayLocationTypeAggregation("Online", "DigitalLocation")
      case LocationTypeAggregation.Physical =>
        DisplayLocationTypeAggregation("In the library", "PhysicalLocation")
    }
}
