package uk.ac.wellcome.platform.matcher.storage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when

import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.platform.matcher.models.{WorkGraph, WorkUpdate}

class WorkGraphStoreTest
    extends AnyFunSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with MatcherFixtures {

  describe("Get graph of linked works") {
    it("returns nothing if there are no matching graphs") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          whenReady(
            workGraphStore.findAffectedWorks(
              WorkUpdate("Not-there", 0, Set.empty))) { workGraph =>
            workGraph shouldBe WorkGraph(Set.empty)
          }
        }
      }
    }

    it(
      "returns a WorkNode if it has no links and it's the only node in the setId") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val work =
            WorkNode(id = "A", version = 0, linkedIds = Nil, componentId = "A")
          put(dynamoClient, graphTable.name)(work)

          whenReady(
            workGraphStore.findAffectedWorks(WorkUpdate("A", 0, Set.empty))) {
            workGraph =>
              workGraph shouldBe WorkGraph(Set(work))
          }
        }
      }
    }

    it("returns a WorkNode and the links in the workUpdate") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workA =
            WorkNode(id = "A", version = 0, linkedIds = Nil, componentId = "A")
          val workB =
            WorkNode(id = "B", version = 0, linkedIds = Nil, componentId = "B")
          put(dynamoClient, graphTable.name)(workA)
          put(dynamoClient, graphTable.name)(workB)

          whenReady(
            workGraphStore.findAffectedWorks(WorkUpdate("A", 0, Set("B")))) {
            workGraph =>
              workGraph.nodes shouldBe Set(workA, workB)
          }
        }
      }
    }

    it("returns a WorkNode and the links in the database") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workA =
            WorkNode(
              id = "A",
              version = 0,
              linkedIds = List("B"),
              componentId = "AB")
          val workB =
            WorkNode(id = "B", version = 0, linkedIds = Nil, componentId = "AB")

          put(dynamoClient, graphTable.name)(workA)
          put(dynamoClient, graphTable.name)(workB)

          whenReady(
            workGraphStore.findAffectedWorks(WorkUpdate("A", 0, Set.empty))) {
            workGraph =>
              workGraph.nodes shouldBe Set(workA, workB)
          }
        }
      }
    }

    it(
      "returns a WorkNode and the links in the database more than one level down") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workA =
            WorkNode(
              id = "A",
              version = 0,
              linkedIds = List("B"),
              componentId = "ABC")
          val workB =
            WorkNode(
              id = "B",
              version = 0,
              linkedIds = List("C"),
              componentId = "ABC")
          val workC = WorkNode(
            id = "C",
            version = 0,
            linkedIds = Nil,
            componentId = "ABC")

          put(dynamoClient, graphTable.name)(workA)
          put(dynamoClient, graphTable.name)(workB)
          put(dynamoClient, graphTable.name)(workC)

          whenReady(
            workGraphStore.findAffectedWorks(WorkUpdate("A", 0, Set.empty))) {
            workGraph =>
              workGraph.nodes shouldBe Set(workA, workB, workC)
          }
        }
      }
    }

    it(
      "returns a WorkNode and the links in the database where an update joins two sets of works") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workA =
            WorkNode(
              id = "A",
              version = 0,
              linkedIds = List("B"),
              componentId = "AB")
          val workB =
            WorkNode(id = "B", version = 0, linkedIds = Nil, componentId = "AB")
          val workC =
            WorkNode(id = "C", version = 0, linkedIds = Nil, componentId = "C")

          put(dynamoClient, graphTable.name)(workA)
          put(dynamoClient, graphTable.name)(workB)
          put(dynamoClient, graphTable.name)(workC)

          whenReady(
            workGraphStore.findAffectedWorks(WorkUpdate("B", 0, Set("C")))) {
            workGraph =>
              workGraph.nodes shouldBe Set(workA, workB, workC)
          }
        }
      }
    }
  }

  describe("Put graph of linked works") {
    it("puts a simple graph") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workNodeA = WorkNode("A", version = 0, List("B"), "A+B")
          val workNodeB = WorkNode("B", version = 0, Nil, "A+B")

          whenReady(workGraphStore.put(WorkGraph(Set(workNodeA, workNodeB)))) {
            _ =>
              val savedWorks = scan[WorkNode](dynamoClient, graphTable.name)
                .map(_.right.get)
              savedWorks should contain theSameElementsAs List(
                workNodeA,
                workNodeB)
          }
        }
      }
    }
  }

  it("throws a RuntimeException if workGraphStore fails to put") {
    val mockWorkNodeDao = mock[WorkNodeDao]
    val expectedException = new RuntimeException("FAILED")
    when(mockWorkNodeDao.put(any[WorkNode]))
      .thenReturn(Future.failed(expectedException))
    val workGraphStore = new WorkGraphStore(mockWorkNodeDao)

    whenReady(
      workGraphStore
        .put(WorkGraph(Set(WorkNode("A", version = 0, Nil, "A+B"))))
        .failed) { failedException =>
      failedException shouldBe expectedException
    }
  }
}
