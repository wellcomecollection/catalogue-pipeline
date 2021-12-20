package weco.pipeline.matcher.generators

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{MergeCandidate, WorkState}
import weco.pipeline.matcher.models.{ComponentId, WorkNode, WorkStub}

import java.time.Instant

trait WorkNodeGenerators extends WorkStubGenerators  {
  def createOneWork(pattern: String): WorkNode =
    pattern match {
      case "A" =>
        WorkNode(
          id = idA,
          version = 0,
          linkedIds = Nil,
          componentId = ComponentId(idA)
        )
    }

  def createTwoWorks(pattern: String): (WorkNode, WorkNode) =
    pattern match {
      case "A->B" =>
        (
          WorkNode(
            idA,
            version = 1,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB)),
          WorkNode(
            idB,
            version = 1,
            linkedIds = Nil,
            componentId = ComponentId(idA, idB))
        )
    }

  def createThreeWorks(pattern: String): (WorkNode, WorkNode, WorkNode) =
    pattern match {
      case "A->B->C" =>
        (
          WorkNode(
            idA,
            version = 1,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB, idC)),
          WorkNode(
            idB,
            version = 1,
            linkedIds = List(idC),
            componentId = ComponentId(idA, idB, idC)),
          WorkNode(
            idC,
            version = 1,
            linkedIds = Nil,
            componentId = ComponentId(idA, idB, idC)),
        )
    }

  implicit class WorkNodeOps(n: WorkNode) {
    def toStub: WorkStub =
      WorkStub(
        state = WorkState.Identified(
          sourceIdentifier = createSourceIdentifier,
          canonicalId = n.id,
          mergeCandidates = n.linkedIds
            .filterNot { _ == n.id }
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
        version = n.version.get,
        workType = "Visible"
      )
  }

//  def createThreeWorks(pattern: String) =
//    pattern match {
//      case "A->B->C"
//    }
}
