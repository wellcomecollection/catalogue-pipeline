package weco.pipeline.matcher.generators

import weco.pipeline.matcher.models.{SourceWorkData, SubgraphId, WorkNode}

trait WorkNodeGenerators extends WorkStubGenerators {
  // These patterns are to make it easier to write simple tests.
  //
  // We're not writing arbitrary pattern parsing code; instead we have some hard-coded
  // examples whose meaning is hopefully obvious that reduces the amount of repetition
  // in our tests.

  def createOneWork(pattern: String): WorkNode =
    pattern match {
      case "A" =>
        WorkNode(
          id = idA,
          subgraphId = SubgraphId(idA),
          componentIds = List(idA),
          sourceWork = SourceWorkData(version = 1),
        )
      case "B" =>
        WorkNode(
          id = idB,
          subgraphId = SubgraphId(idB),
          componentIds = List(idB),
          sourceWork = SourceWorkData(version = 1),
        )
      case "B[suppressed]" =>
        WorkNode(
          id = idB,
          subgraphId = SubgraphId(idB),
          componentIds = List(idB),
          sourceWork = SourceWorkData(version = 1, suppressed = true),
        )
      case "C" =>
        WorkNode(
          id = idC,
          subgraphId = SubgraphId(idC),
          componentIds = List(idC),
          sourceWork = SourceWorkData(version = 1),
        )
      case "D" =>
        WorkNode(
          id = idD,
          subgraphId = SubgraphId(idD),
          componentIds = List(idD),
          sourceWork = SourceWorkData(version = 1),
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
            sourceWork = SourceWorkData(version = 1, mergeCandidateIds = List(idB)),
          ),
          WorkNode(
            id = idB,
            subgraphId = SubgraphId(idA, idB),
            componentIds = List(idA, idB),
            sourceWork = SourceWorkData(version = 1),
          ),
        )
      case "C->D" =>
        (
          WorkNode(
            id = idC,
            subgraphId = SubgraphId(idC, idD),
            componentIds = List(idC, idD),
            sourceWork = SourceWorkData(version = 1, mergeCandidateIds = List(idD)),
          ),
          WorkNode(
            id = idD,
            subgraphId = SubgraphId(idC, idD),
            componentIds = List(idC, idD),
            sourceWork = SourceWorkData(version = 1),
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
            sourceWork = SourceWorkData(version = 1, mergeCandidateIds = List(idB)),
          ),
          WorkNode(
            id = idB,
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC),
            sourceWork = SourceWorkData(version = 1, mergeCandidateIds = List(idC)),
          ),
          WorkNode(
            id = idC,
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC),
            sourceWork = SourceWorkData(version = 1),
          ),
        )
      case "A<->B->C" =>
        (
          WorkNode(
            id = idA,
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC),
            sourceWork = SourceWorkData(version = 1, mergeCandidateIds = List(idB)),
          ),
          WorkNode(
            id = idB,
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC),
            sourceWork = SourceWorkData(version = 1, mergeCandidateIds = List(idA, idC)),
          ),
          WorkNode(
            id = idC,
            subgraphId = SubgraphId(idA, idB, idC),
            componentIds = List(idA, idB, idC),
            sourceWork = SourceWorkData(version = 1),
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
            sourceWork = SourceWorkData(version = 1, mergeCandidateIds = List(idB)),
          ),
          WorkNode(
            id = idB,
            subgraphId = SubgraphId(idA, idB, idC, idD, idE),
            componentIds = List(idA, idB, idC, idD, idE),
            sourceWork = SourceWorkData(version = 1, mergeCandidateIds = List(idC)),
          ),
          WorkNode(
            id = idC,
            subgraphId = SubgraphId(idA, idB, idC, idD, idE),
            componentIds = List(idA, idB, idC, idD, idE),
            sourceWork = SourceWorkData(version = 1, mergeCandidateIds = List(idD)),
          ),
          WorkNode(
            id = idD,
            subgraphId = SubgraphId(idA, idB, idC, idD, idE),
            componentIds = List(idA, idB, idC, idD, idE),
            sourceWork = SourceWorkData(version = 1, mergeCandidateIds = List(idE)),
          ),
          WorkNode(
            id = idE,
            subgraphId = SubgraphId(idA, idB, idC, idD, idE),
            componentIds = List(idA, idB, idC, idD, idE),
            sourceWork = SourceWorkData(version = 1),
          ),
        )
    }
}
