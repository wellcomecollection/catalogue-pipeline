package uk.ac.wellcome.platform.reindex.reindex_worker.services

import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.Scanamo
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.{RecordReaderFixture, ReindexableTable}
import uk.ac.wellcome.platform.reindex.reindex_worker.models.{CompleteReindexParameters, PartialReindexParameters}
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.storage.vhs.Entry

import scala.util.{Failure, Try}

class RecordReaderTest
    extends FunSpec
    with Matchers
    with RecordReaderFixture
    with ReindexableTable {

  val exampleRecord = Entry(
    id = "id",
    version = 1,
    location = ObjectLocation(
      namespace = "s3://example-bukkit",
      key = "key.json.gz"
    ),
    metadata = ReindexerMetadata(name = "anne")
  )

  it("finds records in the table with a complete reindex") {
    withLocalDynamoDbTable { table =>
      withRecordReader { reader =>
        val records = List(
          exampleRecord.copy(id = "id1"),
          exampleRecord.copy(id = "id2")
        )

        records.foreach(record =>
          Scanamo.put(dynamoDbClient)(table.name)(record))

        val reindexParameters = CompleteReindexParameters(
          segment = 0,
          totalSegments = 1
        )

        val result: Try[Seq[String]] = reader.findRecordsForReindexing(
          reindexParameters = reindexParameters,
          dynamoConfig = createDynamoConfigWith(table)
        )

        parseRecords[ReindexerEntry](result) should contain theSameElementsAs records
      }
    }
  }

  it("finds records in the table with a maxResults reindex") {
    withLocalDynamoDbTable { table =>
      withRecordReader { reader =>
        (1 to 15).foreach { id =>
          val record = exampleRecord.copy(id = id.toString)
          Scanamo.put(dynamoDbClient)(table.name)(record)
        }

        val reindexParameters = PartialReindexParameters(maxRecords = 5)

        val result = reader.findRecordsForReindexing(
          reindexParameters = reindexParameters,
          dynamoConfig = createDynamoConfigWith(table)
        )

        parseRecords[ReindexerEntry](result) should have size 5
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
      val result = reader.findRecordsForReindexing(
        reindexParameters = reindexParameters,
        dynamoConfig = createDynamoConfigWith(table)
      )

      result shouldBe a[Failure[_]]
      result.failed.get shouldBe a[ResourceNotFoundException]
    }
  }
}
