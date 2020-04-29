package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.SourceIdentifier

@Schema(
  name = "Identifier",
  description =
    "A unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
)
case class DisplayIdentifier(
  @Schema(description =
    "Relates a Identifier to a particular authoritative source identifier scheme: for example, if the identifier is MS.49 this property might indicate that this identifier has its origins in the Wellcome Library's CALM archive management system.") identifierType: DisplayIdentifierType,
  @Schema(description = "The value of the thing. e.g. an identifier") value: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Identifier"
)

object DisplayIdentifier {
  def apply(sourceIdentifier: SourceIdentifier): DisplayIdentifier =
    DisplayIdentifier(
      identifierType =
        DisplayIdentifierType(identifierType = sourceIdentifier.identifierType),
      value = sourceIdentifier.value)
}
