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
    case DigitalLocation(url, locType, license, credit, accessConditions, _) =>
      DisplayDigitalLocationV2(
        locationType = DisplayLocationType(locType),
        url = url,
        credit = credit,
        license = license.map(DisplayLicenseV2(_)),
        accessConditions =  accessConditions.map(_.map(DisplayAccessCondition(_)))
      )
    case PhysicalLocation(locationType, label, accessConditions,  _) =>
      DisplayPhysicalLocationV2(
        locationType = DisplayLocationType(locationType),
        label = label,
        accessConditions =  accessConditions.map(_.map(DisplayAccessCondition(_)))
      )
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
  ) accessConditions: Option[List[DisplayAccessCondition]] = None,
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "DigitalLocation"
) extends DisplayLocationV2

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
  ) accessConditions: Option[List[DisplayAccessCondition]] = None,
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "PhysicalLocation"
) extends DisplayLocationV2
