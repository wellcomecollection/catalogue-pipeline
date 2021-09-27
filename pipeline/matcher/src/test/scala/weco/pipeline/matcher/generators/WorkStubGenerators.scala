package weco.pipeline.matcher.generators

import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState}
import weco.catalogue.internal_model.work.{MergeCandidate, Work, WorkState}
import weco.elasticsearch.model.IndexId
import weco.pipeline.matcher.models.WorkStub

import java.time.Instant

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
      state = WorkState.Identified(
        sourceIdentifier = id.sourceIdentifier,
        canonicalId = id.canonicalId,
        mergeCandidates = referencedIds
          .filterNot { _ == id }
          .map { id =>
            MergeCandidate(
              id = id,
              reason = "Linked in the matcher tests"
            )
          }
          .toList,
        sourceModifiedTime = Instant.now()
      ),
      version = version
    )


  def createWorkStub: WorkStub =
    createWorkStubWith(
      referencedIds = collectionOf(min = 0) {
        createIdentifier(canonicalId = createCanonicalId)
      }.toSet
    )

  def createWorkWith(id: CanonicalId, version: Int, referencedWorkIds: Set[CanonicalId]): WorkStub =
    createWorkStubWith(
      id = IdState.Identified(
        canonicalId = id,
        sourceIdentifier = createSourceIdentifier
      ),
      version = version,
      referencedIds = referencedWorkIds.map { canonicalId =>
        IdState.Identified(canonicalId = canonicalId, sourceIdentifier = createSourceIdentifier)
      }
    )

  implicit val indexId: IndexId[Work[WorkState.Identified]] =
    (w: Work[WorkState.Identified]) => w.id

  implicit class WorkOps(w: Work[WorkState.Identified]) {
    def asWorkStub: WorkStub =
      WorkStub(state = w.state, version = w.version)
  }
}
