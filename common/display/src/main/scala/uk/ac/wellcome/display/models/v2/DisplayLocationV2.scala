package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  Location,
  PhysicalLocation
}
import uk.ac.wellcome.display.models.DisplayAccessCondition

@Schema(
  name = "Location",
  description = "A location that provides access to an item",
  discriminatorProperty = "type",
  allOf =
    Array(classOf[DisplayDigitalLocationV2], classOf[DisplayPhysicalLocationV2])
)
sealed trait DisplayLocationV2

object DisplayLocationV2 {
  def apply(location: Location): DisplayLocationV2 = location match {
    case digitalLocation: DigitalLocation =>
      DisplayDigitalLocationV2(digitalLocation)
    case physicalLocation: PhysicalLocation =>
      DisplayPhysicalLocationV2(physicalLocation)
  }
}

@Schema(
  name = "DigitalLocation",
  description = "A digital location that provides access to an item"
)
case class DisplayDigitalLocationV2(
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
  ) license: Option[DisplayLicenseV2] = None,
  @Schema(
    description = "Information about any access restrictions placed on the work"
  ) accessConditions: List[DisplayAccessCondition] = Nil,
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "DigitalLocation"
) extends DisplayLocationV2

object DisplayDigitalLocationV2 {
  def apply(location: DigitalLocation): DisplayDigitalLocationV2 =
    DisplayDigitalLocationV2(
      locationType = DisplayLocationType(location.locationType),
      url = location.url,
      credit = location.credit,
      license = location.license.map(DisplayLicenseV2(_)),
      accessConditions =
        location.accessConditions.map(DisplayAccessCondition(_))
    )
}

@Schema(
  name = "PhysicalLocation",
  description = "A physical location that provides access to an item"
)
case class DisplayPhysicalLocationV2(
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
) extends DisplayLocationV2

object DisplayPhysicalLocationV2 {
  def apply(location: PhysicalLocation): DisplayPhysicalLocationV2 =
    DisplayPhysicalLocationV2(
      locationType = DisplayLocationType(location.locationType),
      label = location.label,
      accessConditions =
        location.accessConditions.map(DisplayAccessCondition(_))
    )
}
