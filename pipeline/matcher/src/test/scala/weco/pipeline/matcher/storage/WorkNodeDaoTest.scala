package weco.pipeline.matcher.storage

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when

import javax.naming.ConfigurationException
import org.scalatest.funspec.AnyFunSpec
import org.scanamo.syntax._
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{BatchWriteItemRequest, QueryRequest, ResourceNotFoundException, ScanRequest}
import weco.storage.dynamo.DynamoConfig
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.pipeline.matcher.fixtures.MatcherFixtures
import weco.pipeline.matcher.models.WorkNode

import scala.language.higherKinds
import scala.collection.JavaConverters._

class WorkNodeDaoTest
    extends AnyFunSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with MatcherFixtures
    with IdentifiersGenerators {

  val idA = CanonicalId("AAAAAAAA")
  val idB = CanonicalId("BBBBBBBB")

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
              componentId = ciHash(idA, idB))
          val existingWorkB: WorkNode =
            WorkNode(
              idB,
              version = 0,
              linkedIds = Nil,
              componentId = ciHash(idA, idB))

          put(dynamoClient, table.name)(existingWorkA)
          put(dynamoClient, table.name)(existingWorkB)

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
            componentId = ciHash(idA, idB))
          val existingWorkNodeB: WorkNode =
            WorkNode(
              idB,
              version = 0,
              linkedIds = Nil,
              componentId = ciHash(idA, idB))

          put(dynamoClient, table.name)(existingWorkNodeA)
          put(dynamoClient, table.name)(existingWorkNodeB)

          whenReady(matcherGraphDao.getByComponentIds(Set(ciHash(idA, idB)))) {
            _ shouldBe Set(existingWorkNodeA, existingWorkNodeB)
          }
        }
      }
    }

    it(
      "returns an error if fetching from dynamo fails during a getByComponentIds") {
      withWorkGraphTable { table =>
        val dynamoClient = mock[DynamoDbClient]
        val expectedException = new RuntimeException("FAILED")
        when(dynamoClient.query(any[QueryRequest]))
          .thenThrow(expectedException)
        val workNodeDao = new WorkNodeDao(
          dynamoClient,
          dynamoConfig = createDynamoConfigWith(table)
        )

        whenReady(workNodeDao.getByComponentIds(Set(ciHash(idA, idB))).failed) {
          _ shouldBe expectedException
        }
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
              componentId = ciHash(idA, idB),
              version = "five")
          put(dynamoClient, table.name)(badRecord)

          whenReady(workNodeDao.getByComponentIds(Set(ciHash(idA, idB))).failed) {
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
            componentId = ciHash(idA, idB))
          whenReady(workNodeDao.put(Set(work))) { _ =>
            val savedLinkedWork =
              get[WorkNode](dynamoClient, table.name)("id" === idA)
            savedLinkedWork shouldBe Some(Right(work))
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
              componentId = ciHash(id))
          }

          val future = workNodeDao.put(works.toSet)

          whenReady(future) { _ =>
            val scanRequest =
              ScanRequest
                .builder()
                .tableName(table.name)
                .build()

            val storedItemCount =
              dynamoClient
                .scanPaginator(scanRequest)
                .iterator()
                .asScala
                .toSeq
                .map(_.items().size())
                .sum

            storedItemCount shouldBe works.size
          }
        }
      }
    }

    it("returns an error if Scanamo fails to put a record") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          case class BadRecord(id: CanonicalId, version: String)
          val badRecord: BadRecord = BadRecord(id = idA, version = "six")
          put(dynamoClient, table.name)(badRecord)

          whenReady(workNodeDao.get(Set(idA)).failed) {
            _ shouldBe a[RuntimeException]
          }
        }
      }
    }

    it("returns an error if put to dynamo fails") {
      withWorkGraphTable { table =>
        val dynamoClient = mock[DynamoDbClient]
        val expectedException = new RuntimeException("FAILED")
        when(dynamoClient.batchWriteItem(any[BatchWriteItemRequest]))
          .thenThrow(expectedException)
        val workNodeDao = new WorkNodeDao(
          dynamoClient,
          dynamoConfig = createDynamoConfigWith(table)
        )

        val workNode =
          WorkNode(
            idA,
            version = 1,
            linkedIds = List(idB),
            componentId = ciHash(idA, idB))

        whenReady(workNodeDao.put(Set(workNode)).failed) {
          _ shouldBe expectedException
        }
      }
    }

    it("cannot be instantiated if dynamoConfig.maybeIndex is None") {
      intercept[ConfigurationException] {
        new WorkNodeDao(
          dynamoClient,
          DynamoConfig("something", None)
        )
      }
    }
  }
}
