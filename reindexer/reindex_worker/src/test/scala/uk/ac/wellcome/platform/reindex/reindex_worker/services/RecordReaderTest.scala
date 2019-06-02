package uk.ac.wellcome.platform.reindex.reindex_worker.services

import com.amazonaws.services.dynamodbv2.model._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.RecordReaderFixture
import uk.ac.wellcome.platform.reindex.reindex_worker.models.{
  CompleteReindexParameters,
  PartialReindexParameters
}
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table

class RecordReaderTest
    extends FunSpec
    with ScalaFutures
    with Matchers
    with RecordReaderFixture
    with IntegrationPatience {

  it("finds records in the table with a complete reindex") {
    withLocalDynamoDbTable { table =>
      withRecordReader { reader =>
        val records = createTableRecords(table, count = 2)

        val reindexParameters = CompleteReindexParameters(
          segment = 0,
          totalSegments = 1
        )

        val future = reader.findRecordsForReindexing(
          reindexParameters = reindexParameters,
          dynamoConfig = createDynamoConfigWith(table)
        )

        whenReady(future) { actualRecords =>
          actualRecords.map { fromJson[Record](_).get } should contain theSameElementsAs records
        }
      }
    }
  }

  it("finds records in the table with a maxResults reindex") {
    withLocalDynamoDbTable { table =>
      withRecordReader { reader =>
        createTableRecords(table, count = 15)

        val reindexParameters = PartialReindexParameters(maxRecords = 5)

        val future = reader.findRecordsForReindexing(
          reindexParameters = reindexParameters,
          dynamoConfig = createDynamoConfigWith(table)
        )

        whenReady(future) { actualRecords =>
          actualRecords should have size 5
        }
      }
    }
  }

  it("returns a failed Future if there's a DynamoDB error") {
    val table = Table("does-not-exist", "no-such-index")

    val reindexParameters = CompleteReindexParameters(
      segment = 5,
      totalSegments = 10
    )

    withRecordReader { reader =>
      val future = reader.findRecordsForReindexing(
        reindexParameters = reindexParameters,
        dynamoConfig = createDynamoConfigWith(table)
      )
      whenReady(future.failed) {
        _ shouldBe a[ResourceNotFoundException]
      }
    }
  }
}
