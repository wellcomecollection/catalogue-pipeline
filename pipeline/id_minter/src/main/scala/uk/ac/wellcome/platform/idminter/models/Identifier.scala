package uk.ac.wellcome.platform.idminter.models

import uk.ac.wellcome.models.work.internal.SourceIdentifier

/** Represents a set of identifiers as stored in MySQL */
case class Identifier(
  CanonicalId: String,
  OntologyType: String = "Work",
  SourceSystem: String,
  SourceId: String
)

object Identifier {
  def apply(canonicalId: String,
            sourceIdentifier: SourceIdentifier): Identifier =
    Identifier(
      CanonicalId = canonicalId,
      OntologyType = sourceIdentifier.ontologyType,
      SourceSystem = sourceIdentifier.identifierType.id,
      SourceId = sourceIdentifier.value
    )
}
