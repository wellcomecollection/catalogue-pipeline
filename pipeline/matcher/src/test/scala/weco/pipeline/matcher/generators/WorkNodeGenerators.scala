package weco.pipeline.matcher.generators

import weco.pipeline.matcher.models.{ComponentId, WorkNode}

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
          version = 1,
          linkedIds = Nil,
          componentId = ComponentId(idA)
        )
      case "B" =>
        WorkNode(
          id = idB,
          version = 1,
          linkedIds = Nil,
          componentId = ComponentId(idB)
        )
      case "C" =>
        WorkNode(
          id = idC,
          version = 1,
          linkedIds = Nil,
          componentId = ComponentId(idC)
        )
      case "D" =>
        WorkNode(
          id = idD,
          version = 1,
          linkedIds = Nil,
          componentId = ComponentId(idD)
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
      case "C->D" =>
        (
          WorkNode(
            idC,
            version = 1,
            linkedIds = List(idD),
            componentId = ComponentId(idC, idD)),
          WorkNode(
            idD,
            version = 1,
            linkedIds = Nil,
            componentId = ComponentId(idC, idD))
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
      case "A<->B->C" =>
        (
          WorkNode(
            idA,
            version = 1,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB, idC)),
          WorkNode(
            idB,
            version = 1,
            linkedIds = List(idA, idC),
            componentId = ComponentId(idA, idB, idC)),
          WorkNode(
            idC,
            version = 1,
            linkedIds = Nil,
            componentId = ComponentId(idA, idB, idC)),
        )
    }
}
