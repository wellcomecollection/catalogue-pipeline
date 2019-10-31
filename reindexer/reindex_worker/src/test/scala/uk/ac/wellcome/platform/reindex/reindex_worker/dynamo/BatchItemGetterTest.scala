package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.ReindexDynamoFixtures

class BatchItemGetterTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with ReindexDynamoFixtures {

  it("finds a single specified record") {
    withLocalDynamoDbTable { table =>
      val batchItemGetter = createBatchItemGetter

      val records = createRecords(table, count = 5)
      val specifiedRecord = records.head

      val futureResult =
        batchItemGetter.get(List(specifiedRecord.id))(table.name)

      whenReady(futureResult) { result =>
        result.map { fromJson[NamedRecord](_).get }.head shouldBe specifiedRecord
      }
    }
  }

  it("finds a list of specified records") {
    withLocalDynamoDbTable { table =>
      val batchItemGetter = createBatchItemGetter

      val records = createRecords(table, count = 5)
      val specifiedRecords = records.slice(1, 3)

      val futureResult =
        batchItemGetter.get(specifiedRecords.map { _.id }.toList)(table.name)

      whenReady(futureResult) { result =>
        result
          .map { fromJson[NamedRecord](_).get } shouldEqual specifiedRecords
      }
    }
  }

  it("handles being asked for a non-existent record") {
    withLocalDynamoDbTable { table =>
      val batchItemGetter = createBatchItemGetter

      createRecords(table, count = 5)

      val futureResult =
        batchItemGetter.get(List("bananas"))(table.name)

      whenReady(futureResult) { result =>
        result shouldBe empty
      }
    }
  }
}
