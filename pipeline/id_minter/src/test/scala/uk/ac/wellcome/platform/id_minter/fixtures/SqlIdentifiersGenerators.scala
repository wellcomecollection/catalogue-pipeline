package uk.ac.wellcome.platform.id_minter.fixtures

import uk.ac.wellcome.platform.id_minter.models.Identifier
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.SourceIdentifier

trait SqlIdentifiersGenerators extends IdentifiersGenerators {
  def createSQLIdentifierWith(
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier
  ): Identifier =
    Identifier(
      canonicalId = canonicalId,
      sourceIdentifier = sourceIdentifier
    )

  def createSQLIdentifier: Identifier = createSQLIdentifierWith()
}
