package weco.catalogue.display_model.work

import io.circe.generic.extras.JsonKey
import weco.catalogue.display_model.locations.DisplayLocation
import weco.catalogue.internal_model.work.Holdings

case class DisplayHoldings(
  note: Option[String],
  enumeration: List[String],
  location: Option[DisplayLocation],
  @JsonKey("type") ontologyType: String = "Holdings"
)

case object DisplayHoldings {
  def apply(h: Holdings): DisplayHoldings =
    DisplayHoldings(
      note = h.note,
      enumeration = h.enumeration,
      location = h.location.map { DisplayLocation(_) }
    )
}
