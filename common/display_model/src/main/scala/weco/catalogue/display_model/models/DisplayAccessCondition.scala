package weco.catalogue.display_model.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.locations.AccessCondition

case class DisplayAccessCondition(
  method: DisplayAccessMethod,
  status: Option[DisplayAccessStatus],
  terms: Option[String],
  note: Option[String],
  @JsonKey("type") ontologyType: String = "AccessCondition"
)

object DisplayAccessCondition {

  def apply(accessCondition: AccessCondition): DisplayAccessCondition =
    DisplayAccessCondition(
      method = DisplayAccessMethod(accessCondition.method),
      status = accessCondition.status.map(DisplayAccessStatus.apply),
      terms = accessCondition.terms,
      note = accessCondition.note
    )
}
