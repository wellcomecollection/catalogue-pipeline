package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import weco.catalogue.internal_model.locations.AccessStatus

@Schema(
  name = "AccessStatus"
)
case class DisplayAccessStatus(
  id: String,
  label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "AccessStatus"
)

object DisplayAccessStatus {

  def apply(accessStatus: AccessStatus): DisplayAccessStatus =
    accessStatus match {
      case AccessStatus.Open =>
        DisplayAccessStatus("open", "Open")
      case AccessStatus.OpenWithAdvisory =>
        DisplayAccessStatus("open-with-advisory", "Open with advisory")
      case AccessStatus.Restricted =>
        DisplayAccessStatus("restricted", "Restricted")
      case AccessStatus.ByAppointment =>
        DisplayAccessStatus("by-appointment", "By appointment")
      case AccessStatus.TemporarilyUnavailable =>
        DisplayAccessStatus(
          "temporarily-unavailable",
          "Temporarily unavailable")
      case AccessStatus.Unavailable =>
        DisplayAccessStatus("unavailable", "Unavailable")
      case AccessStatus.Closed =>
        DisplayAccessStatus("closed", "Closed")
      case AccessStatus.LicensedResources =>
        DisplayAccessStatus("licensed-resources", "Licensed resources")
      case AccessStatus.PermissionRequired =>
        DisplayAccessStatus("permission-required", "Permission required")
    }
}
