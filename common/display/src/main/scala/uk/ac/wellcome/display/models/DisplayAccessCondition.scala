package uk.ac.wellcome.display.models

import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.{AccessCondition, AccessStatus}

@Schema(
  name = "AccessCondition"
)
case class DisplayAccessCondition(
  status: DisplayAccessStatus,
  terms: Option[String],
  to: Option[String],
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "AccessCondition"
)

object DisplayAccessCondition {

  def apply(accessCondition: AccessCondition): DisplayAccessCondition =
    DisplayAccessCondition(
      status = DisplayAccessStatus(accessCondition.status),
      terms = accessCondition.terms,
      to = accessCondition.to.map(_.format(ISO_LOCAL_DATE)),
    )
}

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
        DisplayAccessStatus("open-with-advisory", "Open with Advisory")
      case AccessStatus.Restricted =>
        DisplayAccessStatus("restricted", "Restricted")
      case AccessStatus.Closed =>
        DisplayAccessStatus("closed", "Closed")
      case AccessStatus.LicensedResources =>
        DisplayAccessStatus("licensed-resources", "Licensed Resources")
    }
}
