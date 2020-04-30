package uk.ac.wellcome.platform.reindex.reindex_worker.services

import com.amazonaws.services.dynamodbv2.model._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.{
  RecordReaderFixture,
  ReindexableTable
}
import uk.ac.wellcome.platform.reindex.reindex_worker.models.{
  CompleteReindexParameters,
  PartialReindexParameters,
  SpecificReindexParameters
}
import uk.ac.wellcome.storage.fixtures.DynamoFixtures.Table

class RecordReaderTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with RecordReaderFixture
    with ReindexableTable
    with IntegrationPatience {

  it("finds records in the table with a complete reindex") {
    withLocalDynamoDbTable { table =>
      val reader = createRecordReader

      val records = createRecords(table, count = 2)

      val reindexParameters = CompleteReindexParameters(
        segment = 0,
        totalSegments = 1
      )

      val future = reader.findRecordsForReindexing(
        reindexParameters = reindexParameters,
        dynamoConfig = createDynamoConfigWith(table)
      )

      whenReady(future) { actualRecords =>
        actualRecords.map { fromJson[NamedRecord](_).get } should contain theSameElementsAs records
      }
    }
  }

  it("finds records in the table with a maxResults reindex") {
    withLocalDynamoDbTable { table =>
      val reader = createRecordReader

      createRecords(table, count = 15)

      val reindexParameters = PartialReindexParameters(maxRecords = 5)

      val future = reader.findRecordsForReindexing(
        reindexParameters = reindexParameters,
        dynamoConfig = createDynamoConfigWith(table)
      )

      whenReady(future) {
        _ should have size 5
      }
    }
  }

  it("finds records in the table with a specified records reindex") {
    withLocalDynamoDbTable { table =>
      val reader = createRecordReader

      val records = createRecords(table, count = 15)

      val reindexParameters = SpecificReindexParameters(List(records.head.id))

      val future = reader.findRecordsForReindexing(
        reindexParameters = reindexParameters,
        dynamoConfig = createDynamoConfigWith(table)
      )

      whenReady(future) { actualRecords =>
        actualRecords should have length 1
        fromJson[NamedRecord](actualRecords.head).get shouldEqual records.head
      }
    }
  }

  it("returns a failed Future if there's a DynamoDB error") {
    val table = Table("does-not-exist", "no-such-index")

    val reindexParameters = CompleteReindexParameters(
      segment = 5,
      totalSegments = 10
    )

    val reader = createRecordReader

    val future = reader.findRecordsForReindexing(
      reindexParameters = reindexParameters,
      dynamoConfig = createDynamoConfigWith(table)
    )
    whenReady(future.failed) {
      _ shouldBe a[ResourceNotFoundException]
    }
  }
}
