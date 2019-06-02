package uk.ac.wellcome.platform.idminter.fixtures

import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.{IdentifierType, SourceIdentifier}
import uk.ac.wellcome.platform.idminter.models.Identifier

trait IdMinterGenerators extends IdentifiersGenerators {
  def createIdentifierWith(
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier
  ): Identifier =
    Identifier(
      canonicalId = canonicalId,
      sourceIdentifier = sourceIdentifier
    )

  def createIdentifier: Identifier = createIdentifierWith()

  def toSourceIdentifier(identifier: Identifier): SourceIdentifier =
    SourceIdentifier(
      identifierType = IdentifierType(identifier.SourceSystem),
      ontologyType = identifier.OntologyType,
      value = identifier.SourceId
    )
}
