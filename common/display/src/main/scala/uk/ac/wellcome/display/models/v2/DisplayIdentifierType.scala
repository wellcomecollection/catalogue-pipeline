package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.IdentifierType

@Schema(
  name = "IdentifierType"
)
case class DisplayIdentifierType(
  @Schema id: String,
  @Schema label: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "IdentifierType"
)

object DisplayIdentifierType {
  def apply(identifierType: IdentifierType): DisplayIdentifierType =
    DisplayIdentifierType(
      id = identifierType.id,
      label = identifierType.label
    )
}
