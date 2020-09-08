package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{DigitalResourceFormat, Location}

@Schema(
  name = "Location",
  description = "A location that provides access to an item",
  discriminatorProperty = "type",
  allOf = Array(
    classOf[Location.ClosedStores],
    classOf[Location.OpenShelves],
    classOf[Location.DigitalResource])
)
sealed trait DisplayLocation {
  val label: String
  val ontologyType: String
}

object DisplayLocation {
  def apply(location: Location): DisplayLocation = location match {
    case location: Location.ClosedStores =>
      DisplayClosedStores(location)
    case location: Location.OpenShelves => DisplayOpenShelves(location)
    case location: Location.DigitalResource =>
      DisplayDigitalResource(location)
  }

  case class DisplayClosedStores(accessConditions: List[DisplayAccessCondition],
                                 label: String = "Closed stores",
                                 @JsonKey("type") ontologyType: String =
                                   "ClosedStores")
      extends DisplayLocation

  object DisplayClosedStores {
    def apply(location: Location.ClosedStores): DisplayClosedStores =
      DisplayClosedStores(
        accessConditions =
          location.accessConditions.map(DisplayAccessCondition(_)))
  }
  case class DisplayOpenShelves(accessConditions: List[DisplayAccessCondition],
                                shelfmark: String,
                                shelfLocation: String,
                                label: String = "Open shelves",
                                @JsonKey("type") ontologyType: String =
                                  "OpenShelves")
      extends DisplayLocation

  object DisplayOpenShelves {
    def apply(location: Location.OpenShelves): DisplayOpenShelves =
      DisplayOpenShelves(
        accessConditions =
          location.accessConditions.map(DisplayAccessCondition(_)),
        shelfmark = location.shelfmark,
        shelfLocation = location.shelfLocation)
  }

  case class DisplayDigitalResource(
    accessConditions: List[DisplayAccessCondition],
    url: String,
    license: Option[DisplayLicense],
    credit: Option[String],
    format: Option[DisplayDigitalResourceFormat],
    label: String = "Online",
    @JsonKey("type") ontologyType: String = "DigitalLocation")
      extends DisplayLocation

  object DisplayDigitalResource {
    def apply(location: Location.DigitalResource): DisplayDigitalResource =
      DisplayDigitalResource(
        accessConditions =
          location.accessConditions.map(DisplayAccessCondition(_)),
        url = location.url,
        license = location.license.map(DisplayLicense(_)),
        credit = location.credit,
        format = location.format.map(DisplayDigitalResourceFormat(_))
      )
  }
}

case class DisplayDigitalResourceFormat(
  label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String)
object DisplayDigitalResourceFormat {
  def apply(digitalResourceFormat: DigitalResourceFormat)
    : DisplayDigitalResourceFormat = digitalResourceFormat match {
    case DigitalResourceFormat.IIIFPresentation =>
      DisplayDigitalResourceFormat("IIIF presentation", "IIIFPresentation")
    case DigitalResourceFormat.IIIFImage =>
      DisplayDigitalResourceFormat("IIIF image", "IIIFImage")
  }
}
