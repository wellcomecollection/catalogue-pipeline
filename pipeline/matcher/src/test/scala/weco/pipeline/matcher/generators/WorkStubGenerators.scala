package weco.pipeline.matcher.generators

import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState}
import weco.pipeline.matcher.models.WorkStub

trait WorkStubGenerators extends IdentifiersGenerators {
  def createIdentifier(canonicalId: CanonicalId): IdState.Identified =
    IdState.Identified(
      canonicalId = canonicalId,
      sourceIdentifier =
        createSourceIdentifierWith(value = canonicalId.toString)
    )

  def createIdentifier(canonicalId: String): IdState.Identified =
    createIdentifier(canonicalId = CanonicalId(canonicalId))

  def createWorkStubWith(
    id: IdState.Identified = createIdentifier(canonicalId = createCanonicalId),
    version: Int = randomInt(from = 1, to = 10),
    referencedIds: Set[IdState.Identified] = Set.empty
  ): WorkStub =
    WorkStub(
      id = id.canonicalId,
      version = version,
      referencedWorkIds = referencedIds.map { _.canonicalId }
    )

  def createWorkStub: WorkStub =
    createWorkStubWith(
      referencedIds = collectionOf(min = 0) {
        createIdentifier(canonicalId = createCanonicalId)
      }.toSet
    )
}
