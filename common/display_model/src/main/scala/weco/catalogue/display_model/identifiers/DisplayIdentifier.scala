package weco.catalogue.display_model.identifiers

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.SourceIdentifier

case class DisplayIdentifier(
  identifierType: DisplayIdentifierType,
  value: String,
  @JsonKey("type") ontologyType: String = "Identifier"
)

object DisplayIdentifier {
  def apply(sourceIdentifier: SourceIdentifier): DisplayIdentifier =
    DisplayIdentifier(
      identifierType =
        DisplayIdentifierType(identifierType = sourceIdentifier.identifierType),
      value = sourceIdentifier.value
    )
}
