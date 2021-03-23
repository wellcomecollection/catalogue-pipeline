package uk.ac.wellcome.platform.matcher.generators

import uk.ac.wellcome.platform.matcher.models.WorkLinks
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState}

trait WorkLinksGenerators extends IdentifiersGenerators {
  def createIdentifier(canonicalId: CanonicalId): IdState.Identified =
    IdState.Identified(
      canonicalId = canonicalId,
      sourceIdentifier =
        createSourceIdentifierWith(value = canonicalId.toString)
    )

  def createIdentifier(canonicalId: String): IdState.Identified =
    createIdentifier(canonicalId = CanonicalId(canonicalId))

  def createWorkLinksWith(
    id: IdState.Identified = createIdentifier(canonicalId = createCanonicalId),
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
        createIdentifier(canonicalId = createCanonicalId)
      }.toSet
    )
}
