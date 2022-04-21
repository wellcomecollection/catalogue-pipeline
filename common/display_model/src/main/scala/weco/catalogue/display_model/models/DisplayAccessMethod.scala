package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.locations.AccessMethod

case class DisplayAccessMethod(
  id: String,
  label: String,
  @JsonKey("type") ontologyType: String = "AccessMethod"
)

object DisplayAccessMethod {
  def apply(accessMethod: AccessMethod): DisplayAccessMethod =
    accessMethod match {
      case AccessMethod.OnlineRequest =>
        DisplayAccessMethod("online-request", "Online request")
      case AccessMethod.ManualRequest =>
        DisplayAccessMethod("manual-request", "Manual request")
      case AccessMethod.NotRequestable =>
        DisplayAccessMethod("not-requestable", "Not requestable")
      case AccessMethod.ViewOnline =>
        DisplayAccessMethod("view-online", "View online")
      case AccessMethod.OpenShelves =>
        DisplayAccessMethod("open-shelves", "Open shelves")
    }
}
