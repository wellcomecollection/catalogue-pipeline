package weco.catalogue.display_model.locations

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.locations.{
  DigitalLocation,
  Location,
  PhysicalLocation
}

sealed trait DisplayLocation

object DisplayLocation {
  def apply(location: Location): DisplayLocation =
    location match {
      case digitalLocation: DigitalLocation =>
        DisplayDigitalLocation(digitalLocation)
      case physicalLocation: PhysicalLocation =>
        DisplayPhysicalLocation(physicalLocation)
    }
}

case class DisplayDigitalLocation(
  locationType: DisplayLocationType,
  url: String,
  credit: Option[String] = None,
  linkText: Option[String] = None,
  license: Option[DisplayLicense] = None,
  accessConditions: List[DisplayAccessCondition] = Nil,
  @JsonKey("type") ontologyType: String = "DigitalLocation"
) extends DisplayLocation

object DisplayDigitalLocation {
  def apply(location: DigitalLocation): DisplayDigitalLocation =
    DisplayDigitalLocation(
      locationType = DisplayLocationType(location.locationType),
      url = location.url,
      linkText = location.linkText,
      credit = location.credit,
      license = location.license.map(DisplayLicense(_)),
      accessConditions =
        location.accessConditions.map(DisplayAccessCondition(_))
    )
}

case class DisplayPhysicalLocation(
  locationType: DisplayLocationType,
  label: String,
  license: Option[DisplayLicense] = None,
  shelfmark: Option[String] = None,
  accessConditions: List[DisplayAccessCondition] = Nil,
  @JsonKey("type") ontologyType: String = "PhysicalLocation"
) extends DisplayLocation

object DisplayPhysicalLocation {
  def apply(location: PhysicalLocation): DisplayPhysicalLocation =
    DisplayPhysicalLocation(
      locationType = DisplayLocationType(location.locationType),
      label = location.label,
      license = location.license.map(DisplayLicense(_)),
      shelfmark = location.shelfmark,
      accessConditions =
        location.accessConditions.map(DisplayAccessCondition(_))
    )
}
