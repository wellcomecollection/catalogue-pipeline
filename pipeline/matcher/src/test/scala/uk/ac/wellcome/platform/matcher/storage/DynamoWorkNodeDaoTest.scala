package uk.ac.wellcome.platform.matcher.storage

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.Scanamo
import com.gu.scanamo.syntax._
import javax.naming.ConfigurationException
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.matcher.WorkNode
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.storage.dynamo.DynamoConfig

import scala.concurrent.ExecutionContext.Implicits.global
class DynamoWorkNodeDaoTest
    extends FunSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with MatcherFixtures {

  describe("Get from dynamo") {
    it("returns nothing if ids are not in dynamo") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          whenReady(workNodeDao.get(Set("Not-there"))) { workNodeSet =>
            workNodeSet shouldBe Set.empty
          }
        }
      }
    }

    it("returns WorkNodes which are stored in DynamoDB") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          val existingWorkA: WorkNode =
            WorkNode("A", 1, List("B"), "A+B")
          val existingWorkB: WorkNode = WorkNode("B", 0, Nil, "A+B")

          Scanamo.put(dynamoDbClient)(table.name)(existingWorkA)
          Scanamo.put(dynamoDbClient)(table.name)(existingWorkB)

          whenReady(workNodeDao.get(Set("A", "B"))) { work =>
            work shouldBe Set(existingWorkA, existingWorkB)
          }
        }
      }
    }

    it("returns an error if fetching from dynamo fails") {
      withWorkGraphTable { table =>
        val dynamoDbClient = mock[AmazonDynamoDB]
        val expectedException = new RuntimeException("FAILED!")

        when(dynamoDbClient.batchGetItem(any[BatchGetItemRequest]))
          .thenThrow(expectedException)

        val matcherGraphDao = new DynamoWorkNodeDao(
          dynamoDbClient,
          DynamoConfig(table = table.name, index = table.index)
        )

        whenReady(matcherGraphDao.get(Set("A")).failed) { failedException =>
          failedException shouldBe expectedException
        }
      }
    }

    it(
      "returns a GracefulFailure if ProvisionedThroughputExceededException occurs during get from dynamo") {
      withWorkGraphTable { table =>
        val dynamoDbClient = mock[AmazonDynamoDB]
        when(dynamoDbClient.batchGetItem(any[BatchGetItemRequest]))
          .thenThrow(new ProvisionedThroughputExceededException("test"))
        val workNodeDao = new DynamoWorkNodeDao(
          dynamoDbClient,
          DynamoConfig(table.name, table.index)
        )

        whenReady(workNodeDao.get(Set("A")).failed) { failedException =>
          failedException shouldBe a[MatcherException]
        }
      }
    }
  }

  describe("Get by ComponentIds") {
    it("returns empty set if componentIds are not in dynamo") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          whenReady(workNodeDao.getByComponentIds(Set("Not-there"))) {
            workNodeSet =>
              workNodeSet shouldBe Set()
          }
        }
      }
    }

    it(
      "returns WorkNodes which are stored in DynamoDB for a given component id") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { matcherGraphDao =>
          val existingWorkNodeA: WorkNode = WorkNode("A", 1, List("B"), "A+B")
          val existingWorkNodeB: WorkNode = WorkNode("B", 0, Nil, "A+B")

          Scanamo.put(dynamoDbClient)(table.name)(existingWorkNodeA)
          Scanamo.put(dynamoDbClient)(table.name)(existingWorkNodeB)

          whenReady(matcherGraphDao.getByComponentIds(Set("A+B"))) {
            linkedWorks =>
              linkedWorks shouldBe Set(existingWorkNodeA, existingWorkNodeB)
          }
        }
      }
    }

    it(
      "returns an error if fetching from dynamo fails during a getByComponentIds") {
      withWorkGraphTable { table =>
        val dynamoDbClient = mock[AmazonDynamoDB]
        val expectedException = new RuntimeException("FAILED")
        when(dynamoDbClient.query(any[QueryRequest]))
          .thenThrow(expectedException)
        val workNodeDao = new DynamoWorkNodeDao(
          dynamoDbClient,
          DynamoConfig(table = table.name, index = table.index)
        )

        whenReady(workNodeDao.getByComponentIds(Set("A+B")).failed) {
          failedException =>
            failedException shouldBe expectedException
        }
      }
    }

    it(
      "returns a GracefulFailure if ProvisionedThroughputExceededException occurs during a getByComponentIds") {
      withWorkGraphTable { table =>
        val dynamoDbClient = mock[AmazonDynamoDB]
        when(dynamoDbClient.query(any[QueryRequest]))
          .thenThrow(new ProvisionedThroughputExceededException("test"))
        val workNodeDao = new DynamoWorkNodeDao(
          dynamoDbClient,
          DynamoConfig(table.name, table.index)
        )

        whenReady(workNodeDao.getByComponentIds(Set("A+B")).failed) {
          failedException =>
            failedException shouldBe a[MatcherException]
        }
      }
    }

    it("returns an error if Scanamo fails during a getByComponentIds") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          case class BadRecord(id: String, componentId: String)
          val badRecord: BadRecord = BadRecord(id = "A", componentId = "A+B")
          Scanamo.put(dynamoDbClient)(table.name)(badRecord)

          whenReady(workNodeDao.getByComponentIds(Set("A+B")).failed) {
            failedException =>
              failedException shouldBe a[RuntimeException]
          }
        }
      }
    }
  }

  describe("Insert into dynamo") {
    it("puts a WorkNode") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          val work = WorkNode("A", 1, List("B"), "A+B")
          whenReady(workNodeDao.put(work)) { _ =>
            val savedLinkedWork =
              Scanamo.get[WorkNode](dynamoDbClient)(table.name)('id -> "A")
            savedLinkedWork shouldBe Some(Right(work))
          }
        }
      }
    }

    it("returns an error if Scanamo fails to put a record") {
      withWorkGraphTable { table =>
        withWorkNodeDao(table) { workNodeDao =>
          case class BadRecord(id: String)
          val badRecord: BadRecord = BadRecord(id = "A")
          Scanamo.put(dynamoDbClient)(table.name)(badRecord)

          whenReady(workNodeDao.get(Set("A")).failed) { failedException =>
            failedException shouldBe a[RuntimeException]
          }
        }
      }
    }

    it("returns an error if put to dynamo fails") {
      withWorkGraphTable { table =>
        val dynamoDbClient = mock[AmazonDynamoDB]
        val expectedException = new RuntimeException("FAILED")
        when(dynamoDbClient.putItem(any[PutItemRequest]))
          .thenThrow(expectedException)
        val workNodeDao = new DynamoWorkNodeDao(
          dynamoDbClient,
          DynamoConfig(table = table.name, index = table.index)
        )

        whenReady(workNodeDao.put(WorkNode("A", 1, List("B"), "A+B")).failed) {
          failedException =>
            failedException shouldBe expectedException
        }
      }
    }

    it(
      "returns a GracefulFailure if ProvisionedThroughputExceededException occurs during put to dynamo") {
      withWorkGraphTable { table =>
        val dynamoDbClient = mock[AmazonDynamoDB]
        when(dynamoDbClient.putItem(any[PutItemRequest]))
          .thenThrow(new ProvisionedThroughputExceededException("test"))
        val workNodeDao = new DynamoWorkNodeDao(
          dynamoDbClient,
          DynamoConfig(table.name, table.index)
        )

        whenReady(workNodeDao.put(WorkNode("A", 1, List("B"), "A+B")).failed) {
          failedException =>
            failedException shouldBe a[MatcherException]
        }
      }
    }

    it("cannot be instantiated if dynamoConfig.maybeIndex is None") {
      intercept[ConfigurationException] {
        new DynamoWorkNodeDao(
          dynamoClient = dynamoDbClient,
          dynamoConfig = DynamoConfig(table = "something", maybeIndex = None)
        )
      }
    }
  }
}
