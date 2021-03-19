package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import weco.catalogue.internal_model.locations.AccessCondition

@Schema(
  name = "AccessCondition"
)
case class DisplayAccessCondition(
  status: Option[DisplayAccessStatus],
  terms: Option[String],
  to: Option[String],
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "AccessCondition"
)

object DisplayAccessCondition {

  def apply(accessCondition: AccessCondition): DisplayAccessCondition =
    DisplayAccessCondition(
      status = accessCondition.status.map(DisplayAccessStatus.apply),
      terms = accessCondition.terms,
      to = accessCondition.to
    )
}
