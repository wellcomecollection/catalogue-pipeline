package weco.pipeline.matcher.storage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkNodeGenerators
import weco.pipeline.matcher.models.{ComponentId, WorkNode}
import weco.pipeline.matcher.workgraph.WorkGraphUpdater

import scala.concurrent.duration._

class WorkGraphStoreTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with MatcherFixtures
    with WorkNodeGenerators {

  describe("Get graph of linked works") {
    it("returns nothing if there are no matching graphs") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val future = workGraphStore.findAffectedWorks(ids = Set(createCanonicalId))

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
          val work = createOneWork("A")

          putTableItem(work, table = graphTable)

          val future = workGraphStore.findAffectedWorks(ids = Set(work.id))

          whenReady(future) {
            _ shouldBe Set(work)
          }
        }
      }
    }

    it("returns a WorkNode and the links in the workUpdate") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val (workA, workB) = createTwoWorks("A->B")

          putTableItems(items = Seq(workA, workB), table = graphTable)

          val future = workGraphStore.findAffectedWorks(ids = Set(workA.id))

          whenReady(future) {
            _ shouldBe Set(workA, workB)
          }
        }
      }
    }

    it("returns a WorkNode and the links in the database") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val (workA, workB) = createTwoWorks("A->B")

          putTableItems(items = Seq(workA, workB), table = graphTable)

          val future = workGraphStore.findAffectedWorks(ids = Set(workA.id))

          whenReady(future) {
            _ shouldBe Set(workA, workB)
          }
        }
      }
    }

    it("finds all the affected works, from anywhere in the component") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          val (workA, workB, workC) = createThreeWorks("A->B->C")

          putTableItems(items = Seq(workA, workB, workC), table = graphTable)

          val works = Set(workA, workB, workC)

          works.foreach { w =>
            println(s"Finding affected works for ${w.id}")

            val future = workGraphStore.findAffectedWorks(ids = Set(w.id))

            whenReady(future) {
              _ shouldBe works
            }
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
          whenReady(workGraphStore.findAffectedWorks(ids = workB.ids)) { affectedNodes =>
            val updatedNodes = WorkGraphUpdater.update(workB, affectedNodes)
            Await.ready(workGraphStore.put(updatedNodes), atMost = 1 second)
          }

          // Then store A
          whenReady(workGraphStore.findAffectedWorks(ids = workA.ids)) { affectedNodes =>
            val updatedNodes = WorkGraphUpdater.update(workA, affectedNodes)
            Await.ready(workGraphStore.put(updatedNodes), atMost = 1 second)
          }

          getExistingTableItem[WorkNode](id = idC.underlying, graphTable).suppressed shouldBe true

          whenReady(workGraphStore.findAffectedWorks(ids = workA.ids)) { affectedNodes =>
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
          val (workA, workB) = createTwoWorks("A->B")
          val works = Set(workA, workB)

          val future = workGraphStore.put(works)

          whenReady(future) { _ =>
            val savedWorks = scanTable[WorkNode](graphTable)
              .map(_.right.get)
            savedWorks should contain theSameElementsAs works
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
      componentId = ComponentId(idA, idB))

    whenReady(workGraphStore.put(Set(workNode)).failed) {
      _ shouldBe expectedException
    }
  }
}
