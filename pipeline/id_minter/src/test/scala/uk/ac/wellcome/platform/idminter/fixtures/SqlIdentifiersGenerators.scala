package uk.ac.wellcome.platform.idminter.fixtures

import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.SourceIdentifier
import uk.ac.wellcome.platform.idminter.models.Identifier

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
