package weco.pipeline.id_minter.fixtures

import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{CanonicalId, SourceIdentifier}
import weco.pipeline.id_minter.models.Identifier

trait SqlIdentifiersGenerators extends IdentifiersGenerators {
  def createSQLIdentifierWith(
    canonicalId: CanonicalId = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier
  ): Identifier =
    Identifier(
      canonicalId = canonicalId,
      sourceIdentifier = sourceIdentifier
    )

  def createSQLIdentifier: Identifier = createSQLIdentifierWith()
}
