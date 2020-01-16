package uk.ac.wellcome.platform.idminter.models

import uk.ac.wellcome.models.work.internal.SourceIdentifier

case class Identifier(
                       canonicalId: String,
                       ontologyType: String = "Work",
                       SourceSystem: String,
                       SourceId: String
)

object Identifier {
  def apply(canonicalId: String,
            sourceIdentifier: SourceIdentifier): Identifier =
    Identifier(
      canonicalId = canonicalId,
      ontologyType = sourceIdentifier.ontologyType,
      SourceSystem = sourceIdentifier.identifierType.id,
      SourceId = sourceIdentifier.value
    )
}
