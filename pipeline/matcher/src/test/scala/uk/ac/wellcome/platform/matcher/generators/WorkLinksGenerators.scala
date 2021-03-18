package uk.ac.wellcome.platform.matcher.generators

import uk.ac.wellcome.platform.matcher.models.WorkLinks
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.IdState

trait WorkLinksGenerators extends IdentifiersGenerators {
  def createIdentifier(id: String): IdState.Identified =
    IdState.Identified(
      canonicalId = id,
      sourceIdentifier = createSourceIdentifierWith(value = id)
    )

  def createWorkLinksWith(
    id: IdState.Identified = createIdentifier(randomAlphanumeric()),
    version: Int = randomInt(from = 1, to = 10),
    referencedIds: Set[IdState.Identified] = Set.empty
  ): WorkLinks =
    WorkLinks(
      workId = id.canonicalId,
      version = version,
      referencedWorkIds = referencedIds.map { _.canonicalId }
    )

  def createWorkLinks: WorkLinks =
    createWorkLinksWith(
      referencedIds = collectionOf(min = 0) {
        createIdentifier(randomAlphanumeric())
      }.toSet
    )
}
