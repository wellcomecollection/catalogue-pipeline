package weco.pipeline.matcher.storage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.WorkNode
import weco.pipeline.matcher.workgraph.WorkGraphUpdater

import scala.concurrent.duration._

class WorkGraphStoreTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with MatcherFixtures
    with WorkStubGenerators {

  describe("Get graph of linked works") {
    it("returns nothing if there are no matching graphs") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val future = workGraphStore.findAffectedWorks(
            createWorkWith(
              id = createCanonicalId,
              version = 0,
              referencedWorkIds = Set.empty))

          whenReady(future) {
            _ shouldBe empty
          }
        }
      }
    }

    it(
      "returns a WorkNode if it has no links and it's the only node in the setId") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val work =
            WorkNode(
              id = idA,
              version = 0,
              linkedIds = Nil,
              componentId = ciHash(idA))

          putTableItem(work, table = graphTable)

          val future =
            workGraphStore.findAffectedWorks(createWorkWith(idA, 0, Set.empty))

          whenReady(future) {
            _ shouldBe Set(work)
          }
        }
      }
    }

    it("returns a WorkNode and the links in the workUpdate") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workA =
            WorkNode(
              id = idA,
              version = 0,
              linkedIds = Nil,
              componentId = ciHash(idA))
          val workB =
            WorkNode(
              id = idB,
              version = 0,
              linkedIds = Nil,
              componentId = ciHash(idB))

          putTableItems(items = Seq(workA, workB), table = graphTable)

          val future =
            workGraphStore.findAffectedWorks(createWorkWith(idA, 0, Set(idB)))

          whenReady(future) {
            _ shouldBe Set(workA, workB)
          }
        }
      }
    }

    it("returns a WorkNode and the links in the database") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workA =
            WorkNode(
              id = idA,
              version = 0,
              linkedIds = List(idB),
              componentId = ciHash(idA, idB))
          val workB =
            WorkNode(
              id = idB,
              version = 0,
              linkedIds = Nil,
              componentId = ciHash(idA, idB))

          putTableItems(items = Seq(workA, workB), table = graphTable)

          val future =
            workGraphStore.findAffectedWorks(
              createWorkWith(idA, version = 0, referencedWorkIds = Set.empty))

          whenReady(future) {
            _ shouldBe Set(workA, workB)
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
              id = idA,
              version = 0,
              linkedIds = List(idB),
              componentId = ciHash(idA, idB, idC))
          val workB =
            WorkNode(
              id = idB,
              version = 0,
              linkedIds = List(idC),
              componentId = ciHash(idA, idB, idC))
          val workC = WorkNode(
            id = idC,
            version = 0,
            linkedIds = Nil,
            componentId = ciHash(idA, idB, idC))

          putTableItems(items = Seq(workA, workB, workC), table = graphTable)

          val future =
            workGraphStore.findAffectedWorks(createWorkWith(idA, 0, Set.empty))

          whenReady(future) {
            _ shouldBe Set(workA, workB, workC)
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
              id = idA,
              version = 0,
              linkedIds = List(idB),
              componentId = ciHash(idA, idB))
          val workB =
            WorkNode(
              id = idB,
              version = 0,
              linkedIds = Nil,
              componentId = ciHash(idA, idB))
          val workC =
            WorkNode(
              id = idC,
              version = 0,
              linkedIds = Nil,
              componentId = ciHash(idC))

          putTableItems(items = Seq(workA, workB, workC), table = graphTable)

          val work =
            createWorkWith(idB, version = 0, referencedWorkIds = Set(idC))

          whenReady(workGraphStore.findAffectedWorks(work)) {
            _ shouldBe Set(workA, workB, workC)
          }
        }
      }
    }

    it("retrieves a suppressed node after it was written") {
      // These works form the graph
      //
      //      A -> B -> C
      //
      // but C is suppressed.  We want to make sure updating A still allows
      // us to retrieve C.
      val workC = createWorkWith(id = idC, workType = "Deleted")
      val workB = createWorkWith(id = idB, referencedWorkIds = Set(workC.id))
      val workA = createWorkWith(id = idA, referencedWorkIds = Set(workB.id))

      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>

          // First store C in the table
          val nodesC = WorkGraphUpdater.update(workC, affectedNodes = Set())
          nodesC.head.suppressed shouldBe true
          Await.ready(workGraphStore.put(nodesC), atMost = 1 second)

          // Then store B
          whenReady(workGraphStore.findAffectedWorks(workB)) { affectedNodes =>
            val updatedNodes = WorkGraphUpdater.update(workB, affectedNodes)
            Await.ready(workGraphStore.put(updatedNodes), atMost = 1 second)
          }

          // Then store A
          whenReady(workGraphStore.findAffectedWorks(workA)) { affectedNodes =>
            val updatedNodes = WorkGraphUpdater.update(workA, affectedNodes)
            Await.ready(workGraphStore.put(updatedNodes), atMost = 1 second)
          }

          getExistingTableItem[WorkNode](id = idC.underlying, graphTable).suppressed shouldBe true

          whenReady(workGraphStore.findAffectedWorks(workA)) { affectedNodes =>
            affectedNodes.map(_.id) should contain(workC.id)
          }
        }
      }
    }
  }

  describe("Put graph of linked works") {
    it("puts a simple graph") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val workNodeA = WorkNode(
            idA,
            version = 0,
            linkedIds = List(idB),
            componentId = ciHash(idA, idB))
          val workNodeB = WorkNode(
            idB,
            version = 0,
            linkedIds = Nil,
            componentId = ciHash(idA, idB))

          whenReady(workGraphStore.put(Set(workNodeA, workNodeB))) { _ =>
            val savedWorks = scanTable[WorkNode](graphTable)
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

    val workNode = WorkNode(
      idA,
      version = 0,
      linkedIds = Nil,
      componentId = ciHash(idA, idB))

    whenReady(workGraphStore.put(Set(workNode)).failed) {
      _ shouldBe expectedException
    }
  }
}
