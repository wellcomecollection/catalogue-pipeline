package weco.pipeline.matcher.generators

import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{CanonicalId, IdState}
import weco.catalogue.internal_model.work.{MergeCandidate, Work, WorkState}
import weco.elasticsearch.model.IndexId
import weco.pipeline.matcher.models.WorkStub

import java.time.Instant

trait WorkStubGenerators extends IdentifiersGenerators {

  val idA = CanonicalId("AAAAAAAA")
  val idB = CanonicalId("BBBBBBBB")
  val idC = CanonicalId("CCCCCCCC")
  val idD = CanonicalId("DDDDDDDD")
  val idE = CanonicalId("EEEEEEEE")

  def createWorkStub: WorkStub =
    createWorkWith(
      mergeCandidateIds = collectionOf(min = 0) { createCanonicalId }.toSet
    )

  def createWorkWith(id: CanonicalId = createCanonicalId,
                     version: Int = randomInt(from = 1, to = 10),
                     mergeCandidateIds: Set[CanonicalId] = Set(),
                     workType: String = "Visible"): WorkStub =
    WorkStub(
      state = WorkState.Identified(
        sourceIdentifier = createSourceIdentifier,
        canonicalId = id,
        mergeCandidates = mergeCandidateIds
          .filterNot { _ == id }
          .map { canonicalId =>
            IdState.Identified(
              canonicalId = canonicalId,
              sourceIdentifier = createSourceIdentifier)
          }
          .map { id =>
            MergeCandidate(
              id = id,
              reason = "Linked in the matcher tests"
            )
          }
          .toList,
        sourceModifiedTime = Instant.now()
      ),
      version = version,
      workType = workType
    )

  implicit val indexId: IndexId[Work[WorkState.Identified]] =
    (w: Work[WorkState.Identified]) => w.id
}
