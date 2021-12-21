package weco.pipeline.matcher.generators

import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.pipeline.matcher.models.{SourceWorkData, SubgraphId, WorkNode}

trait WorkNodeGenerators extends WorkStubGenerators {
  // These patterns are to make it easier to write simple tests.
  //
  // We're not writing arbitrary pattern parsing code; instead we have some hard-coded
  // examples whose meaning is hopefully obvious that reduces the amount of repetition
  // in our tests.

  private def createSourceWork(id: CanonicalId): SourceWorkData =
    SourceWorkData(id = sourceIdentifierFrom(id), version = 1)

  def createOneWork(pattern: String): WorkNode =
    pattern match {
      case "A" =>
        WorkNode(
          id = idA,
          subgraphId = SubgraphId(idA),
          componentIds = List(idA),
          sourceWork = createSourceWork(idA),
        )
      case "B" =>
        WorkNode(
          id = idB,
          subgraphId = SubgraphId(idB),
          componentIds = List(idB),
          sourceWork = createSourceWork(idB),
        )
      case "B[suppressed]" =>
        WorkNode(
          id = idB,
          subgraphId = SubgraphId(idB),
          componentIds = List(idB),
          sourceWork = createSourceWork(idB).copy(suppressed = true),
        )
      case "C" =>
        WorkNode(
          id = idC,
          subgraphId = SubgraphId(idC),
          componentIds = List(idC),
          sourceWork = createSourceWork(idC),
        )
      case "D" =>
        WorkNode(
          id = idD,
          subgraphId = SubgraphId(idD),
          componentIds = List(idD),
          sourceWork = createSourceWork(idD),
        )
    }

  def createTwoWorks(pattern: String): (WorkNode, WorkNode) =
    pattern match {
      case "A->B" =>
        (
          WorkNode(
            id = idA,
            subgraphId = SubgraphId(idA, idB),
            componentIds = List(idA, idB),
            sourceWork = createSourceWork(idA).copy(mergeCandidateIds = List(idB)),
          ),
          WorkNode(
            id = idB,
            subgraphId = SubgraphId(idA, idB),
            componentIds = List(idA, idB),
            sourceWork = createSourceWork(idB),
          ),
        )
      case "C->D" =>
        (
          WorkNode(
            id = idC,
            subgraphId = SubgraphId(idC, idD),
            componentIds = List(idC, idD),
            sourceWork = createSourceWork(idC).copy(mergeCandidateIds = List(idD)),
          ),
          WorkNode(
            id = idD,
            subgraphId = SubgraphId(idC, idD),
            componentIds = List(idC, idD),
            sourceWork = createSourceWork(idD),
          ),
        )
    }

  def createThreeWorks(pattern: String): (WorkNode, WorkNode, WorkNode) =
    pattern match {
      case "A->B->C" =>
        (
          WorkNode(
            id = idA,
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC),
            sourceWork = createSourceWork(idA).copy(mergeCandidateIds = List(idB)),
          ),
          WorkNode(
            id = idB,
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC),
            sourceWork = createSourceWork(idB).copy(mergeCandidateIds = List(idC)),
          ),
          WorkNode(
            id = idC,
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC),
            sourceWork = createSourceWork(idC),
          ),
        )
      case "A<->B->C" =>
        (
          WorkNode(
            id = idA,
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC),
            sourceWork = createSourceWork(idA).copy(mergeCandidateIds = List(idB)),
          ),
          WorkNode(
            id = idB,
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC),
            sourceWork = createSourceWork(idB).copy(mergeCandidateIds = List(idA, idC)),
          ),
          WorkNode(
            id = idC,
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC),
            sourceWork = createSourceWork(idC),
          ),
        )
    }

  def createFiveWorks(pattern: String): (WorkNode, WorkNode, WorkNode, WorkNode, WorkNode) =
    pattern match {
      case "A->B->C->D->E" =>
        (
          WorkNode(
            id = idA,
            subgraphId = SubgraphId(idA, idB, idC, idD, idE),
            componentIds = List(idA, idB, idC, idD, idE),
            sourceWork = createSourceWork(idA).copy(mergeCandidateIds = List(idB)),
          ),
          WorkNode(
            id = idB,
            subgraphId = SubgraphId(idA, idB, idC, idD, idE),
            componentIds = List(idA, idB, idC, idD, idE),
            sourceWork = createSourceWork(idB).copy(mergeCandidateIds = List(idC)),
          ),
          WorkNode(
            id = idC,
            subgraphId = SubgraphId(idA, idB, idC, idD, idE),
            componentIds = List(idA, idB, idC, idD, idE),
            sourceWork = createSourceWork(idC).copy(mergeCandidateIds = List(idD)),
          ),
          WorkNode(
            id = idD,
            subgraphId = SubgraphId(idA, idB, idC, idD, idE),
            componentIds = List(idA, idB, idC, idD, idE),
            sourceWork = createSourceWork(idD).copy(mergeCandidateIds = List(idE)),
          ),
          WorkNode(
            id = idE,
            subgraphId = SubgraphId(idA, idB, idC, idD, idE),
            componentIds = List(idA, idB, idC, idD, idE),
            sourceWork = createSourceWork(idE),
          ),
        )
    }

  implicit class WorkNodeOps(w: WorkNode) {
    def updateSourceWork(version: Int, mergeCandidateIds: Set[CanonicalId] = Set(), suppressed: Boolean = false): WorkNode =
      w.copy(
        sourceWork = Some(w.sourceWork.get.copy(
          version = version,
          mergeCandidateIds = mergeCandidateIds.toList.sorted,
          suppressed = suppressed
        ))
      )
  }
}
