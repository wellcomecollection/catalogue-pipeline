package weco.catalogue.display_model.locations

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.locations.AccessStatus

case class DisplayAccessStatus(
  id: String,
  label: String,
  @JsonKey("type") ontologyType: String = "AccessStatus"
)

object DisplayAccessStatus {
  def apply(accessStatus: AccessStatus): DisplayAccessStatus =
    DisplayAccessStatus(
      id = accessStatus.id,
      label = accessStatus.label
    )
}
