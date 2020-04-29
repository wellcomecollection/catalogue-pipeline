package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  Location,
  PhysicalLocation
}

@Schema(
  name = "Location",
  description = "A location that provides access to an item",
  discriminatorProperty = "type",
  allOf =
    Array(classOf[DisplayDigitalLocation], classOf[DisplayPhysicalLocation])
)
sealed trait DisplayLocation

object DisplayLocation {
  def apply(location: Location): DisplayLocation = location match {
    case digitalLocation: DigitalLocation =>
      DisplayDigitalLocation(digitalLocation)
    case physicalLocation: PhysicalLocation =>
      DisplayPhysicalLocation(physicalLocation)
  }
}

@Schema(
  name = "DigitalLocation",
  description = "A digital location that provides access to an item"
)
case class DisplayDigitalLocation(
  @Schema(
    description = "The type of location that an item is accessible from.",
    allowableValues = Array("thumbnail-image", "iiif-image")
  ) locationType: DisplayLocationType,
  @Schema(
    `type` = "String",
    description = "The URL of the digital asset."
  ) url: String,
  @Schema(
    `type` = "String",
    description = "Who to credit the image to"
  ) credit: Option[String] = None,
  @Schema(
    description =
      "The specific license under which the work in question is released to the public - for example, one of the forms of Creative Commons - if it is a precise license to which a link can be made."
  ) license: Option[DisplayLicense] = None,
  @Schema(
    description = "Information about any access restrictions placed on the work"
  ) accessConditions: List[DisplayAccessCondition] = Nil,
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "DigitalLocation"
) extends DisplayLocation

object DisplayDigitalLocation {
  def apply(location: DigitalLocation): DisplayDigitalLocation =
    DisplayDigitalLocation(
      locationType = DisplayLocationType(location.locationType),
      url = location.url,
      credit = location.credit,
      license = location.license.map(DisplayLicense(_)),
      accessConditions =
        location.accessConditions.map(DisplayAccessCondition(_))
    )
}

@Schema(
  name = "PhysicalLocation",
  description = "A physical location that provides access to an item"
)
case class DisplayPhysicalLocation(
  @Schema(
    description = "The type of location that an item is accessible from.",
  ) locationType: DisplayLocationType,
  @Schema(
    `type` = "String",
    description = "The title or other short name of the location."
  ) label: String,
  @Schema(
    description = "Information about any access restrictions placed on the work"
  ) accessConditions: List[DisplayAccessCondition] = Nil,
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "PhysicalLocation"
) extends DisplayLocation

object DisplayPhysicalLocation {
  def apply(location: PhysicalLocation): DisplayPhysicalLocation =
    DisplayPhysicalLocation(
      locationType = DisplayLocationType(location.locationType),
      label = location.label,
      accessConditions =
        location.accessConditions.map(DisplayAccessCondition(_))
    )
}
