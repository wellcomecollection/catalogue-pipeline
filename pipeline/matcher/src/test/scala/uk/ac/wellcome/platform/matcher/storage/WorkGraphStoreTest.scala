package uk.ac.wellcome.platform.matcher.storage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.platform.matcher.models.{WorkGraph, WorkLinks}

class WorkGraphStoreTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with MatcherFixtures {

  describe("Get graph of linked works") {
    it("returns nothing if there are no matching graphs") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          whenReady(
            workGraphStore.findAffectedWorks(
              WorkLinks("Not-there", version = 0, referencedWorkIds = Set.empty))) { workGraph =>
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
            workGraphStore.findAffectedWorks(WorkLinks("A", 0, Set.empty))) {
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
            workGraphStore.findAffectedWorks(WorkLinks("A", 0, Set("B")))) {
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
            workGraphStore.findAffectedWorks(WorkLinks("A", version = 0, referencedWorkIds = Set.empty))) {
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
            workGraphStore.findAffectedWorks(WorkLinks("A", 0, Set.empty))) {
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

          val links = WorkLinks(idB, version = 0, referencedWorkIds = Set(idC))

          whenReady(
            workGraphStore.findAffectedWorks(links)) {
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
          val workNodeA = WorkNode("A", version = 0, linkedIds = List("B"), componentId = "A+B")
          val workNodeB = WorkNode("B", version = 0, linkedIds = Nil, componentId = "A+B")

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
    val expectedException = new RuntimeException("FAILED")

    val brokenWorkNodeDao = new WorkNodeDao(
      dynamoClient,
      dynamoConfig = createDynamoConfigWith(nonExistentTable)
    ) {
      override def put(nodes: Set[WorkNode]): Future[Unit] =
        Future.failed(expectedException)
    }

    val workGraphStore = new WorkGraphStore(brokenWorkNodeDao)

    val workNode = WorkNode("A", version = 0, linkedIds = Nil, componentId = "A+B")

    whenReady(
      workGraphStore
        .put(WorkGraph(Set(workNode)))
        .failed) { failedException =>
      failedException shouldBe expectedException
    }
  }
}
