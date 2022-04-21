package weco.catalogue.display_model.identifiers

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.IdentifierType

case class DisplayIdentifierType(
  id: String,
  label: String,
  @JsonKey("type") ontologyType: String = "IdentifierType"
)

object DisplayIdentifierType {
  def apply(identifierType: IdentifierType): DisplayIdentifierType =
    DisplayIdentifierType(
      id = identifierType.id,
      label = identifierType.label
    )
}
