package weco.pipeline.matcher.storage

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.generators.WorkStubGenerators
import weco.pipeline.matcher.models.{ComponentId, WorkNode}
import weco.storage.dynamo.DynamoConfig

import javax.naming.ConfigurationException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.higherKinds

class WorkNodeDaoTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with MatcherFixtures
    with WorkStubGenerators {

  describe("Get from dynamo") {
    it("returns nothing if ids are not in dynamo") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          whenReady(workNodeDao.get(Set(createCanonicalId))) {
            _ shouldBe Set.empty
          }
        }
      }
    }

    it("returns WorkNodes which are stored in DynamoDB") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          val existingWorkA: WorkNode =
            WorkNode(
              idA,
              version = 1,
              linkedIds = List(idB),
              componentId = ComponentId(idA, idB))
          val existingWorkB: WorkNode =
            WorkNode(
              idB,
              version = 0,
              linkedIds = Nil,
              componentId = ComponentId(idA, idB))

          putTableItems(
            items = Seq(existingWorkA, existingWorkB),
            table = table)

          whenReady(workNodeDao.get(Set(idA, idB))) {
            _ shouldBe Set(existingWorkA, existingWorkB)
          }
        }
      }
    }

    it("returns an error if fetching from dynamo fails") {
      val matcherGraphDao = new WorkNodeDao(
        dynamoClient,
        dynamoConfig = createDynamoConfigWith(nonExistentTable)
      )

      whenReady(matcherGraphDao.get(Set(idA)).failed) {
        _ shouldBe a[ResourceNotFoundException]
      }
    }
  }

  describe("Get by ComponentIds") {
    it("returns empty set if componentIds are not in dynamo") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          whenReady(workNodeDao.getByComponentIds(Set("Not-there"))) {
            _ shouldBe Set()
          }
        }
      }
    }

    it(
      "returns WorkNodes which are stored in DynamoDB for a given component id") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { matcherGraphDao =>
          val existingWorkNodeA: WorkNode = WorkNode(
            idA,
            version = 1,
            linkedIds = List(idB),
            componentId = ComponentId(idA, idB))
          val existingWorkNodeB: WorkNode =
            WorkNode(
              idB,
              version = 0,
              linkedIds = Nil,
              componentId = ComponentId(idA, idB))

          putTableItems(
            items = Seq(existingWorkNodeA, existingWorkNodeB),
            table = table)

          whenReady(
            matcherGraphDao.getByComponentIds(Set(ComponentId(idA, idB)))) {
            _ shouldBe Set(existingWorkNodeA, existingWorkNodeB)
          }
        }
      }
    }

    it("fails if fetching from dynamo fails during a getByComponentIds") {
      val workNodeDao = new WorkNodeDao(
        dynamoClient,
        dynamoConfig = createDynamoConfigWith(nonExistentTable)
      )

      whenReady(
        workNodeDao.getByComponentIds(Set(ComponentId(idA, idB))).failed) {
        _ shouldBe a[ResourceNotFoundException]
      }
    }

    it("returns an error if Scanamo fails during a getByComponentIds") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          case class BadRecord(id: CanonicalId,
                               componentId: String,
                               version: String)
          val badRecord: BadRecord =
            BadRecord(
              id = idA,
              componentId = ComponentId(idA, idB),
              version = "five")

          putTableItem(badRecord, table = table)

          whenReady(
            workNodeDao.getByComponentIds(Set(ComponentId(idA, idB))).failed) {
            _ shouldBe a[RuntimeException]
          }
        }
      }
    }
  }

  describe("Insert into dynamo") {
    it("puts a WorkNode") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          val work = WorkNode(
            idA,
            version = 1,
            linkedIds = List(idA),
            componentId = ComponentId(idA, idB))
          whenReady(workNodeDao.put(Set(work))) { _ =>
            getTableItem[WorkNode](idA.underlying, table) shouldBe Some(
              Right(work))
          }
        }
      }
    }

    it("puts lots of WorkNodes (>25 = a single BatchPutItem)") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          val works = (1 to 50).map { _ =>
            val id = createCanonicalId
            WorkNode(
              id,
              version = 1,
              linkedIds = List(id),
              componentId = ComponentId(id))
          }

          val future = workNodeDao.put(works.toSet)

          whenReady(future) { _ =>
            val storedWorks = scanTable[WorkNode](table).collect {
              case Right(w) => w
            }

            storedWorks should contain theSameElementsAs works
          }
        }
      }
    }

    it("returns an error if Scanamo fails to put a record") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          case class BadRecord(id: CanonicalId, version: String)
          val badRecord: BadRecord = BadRecord(id = idA, version = "six")

          putTableItem(badRecord, table = table)

          whenReady(workNodeDao.get(Set(idA)).failed) {
            _ shouldBe a[RuntimeException]
          }
        }
      }
    }

    it("returns an error if put to dynamo fails") {
      val workNodeDao = new WorkNodeDao(
        dynamoClient,
        dynamoConfig = createDynamoConfigWith(nonExistentTable)
      )

      val workNode =
        WorkNode(
          idA,
          version = 1,
          linkedIds = List(idB),
          componentId = ComponentId(idA, idB))

      whenReady(workNodeDao.put(Set(workNode)).failed) {
        _ shouldBe a[ResourceNotFoundException]
      }
    }

    it("cannot be instantiated if dynamoConfig.maybeIndex is None") {
      intercept[ConfigurationException] {
        new WorkNodeDao(
          dynamoClient,
          DynamoConfig("something", maybeIndexName = None)
        )
      }
    }
  }
}
