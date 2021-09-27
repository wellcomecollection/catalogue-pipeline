package weco.pipeline.matcher.storage

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.WorkNode

class WorkGraphStoreTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with MatcherFixtures
    with WorkStubGenerators {

  val idA = CanonicalId("AAAAAAAA")
  val idB = CanonicalId("BBBBBBBB")
  val idC = CanonicalId("CCCCCCCC")

  describe("Get graph of linked works") {
    it("returns nothing if there are no matching graphs") {
      withWorkGraphTable { graphTable =>
        withWorkGraphStore(graphTable) { workGraphStore =>
          whenReady(
            workGraphStore.findAffectedWorks(
              createWorkWith(
                id = createCanonicalId,
                version = 0,
                referencedWorkIds = Set.empty))) {
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
          put(dynamoClient, graphTable.name)(work)

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
          put(dynamoClient, graphTable.name)(workA)
          put(dynamoClient, graphTable.name)(workB)

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

          put(dynamoClient, graphTable.name)(workA)
          put(dynamoClient, graphTable.name)(workB)

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

          put(dynamoClient, graphTable.name)(workA)
          put(dynamoClient, graphTable.name)(workB)
          put(dynamoClient, graphTable.name)(workC)

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

          put(dynamoClient, graphTable.name)(workA)
          put(dynamoClient, graphTable.name)(workB)
          put(dynamoClient, graphTable.name)(workC)

          val work =
            createWorkWith(idB, version = 0, referencedWorkIds = Set(idC))

          whenReady(workGraphStore.findAffectedWorks(work)) {
            _ shouldBe Set(workA, workB, workC)
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
