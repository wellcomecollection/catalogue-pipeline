package uk.ac.wellcome.platform.reindex.reindex_worker.services

import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.auto._
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

import scala.concurrent.ExecutionContext.Implicits.global

class NewRecordReaderTest
  extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with RecordReaderFixture
    with ReindexableTable
    with IntegrationPatience {
  
  val reader = new NewRecordReader()

  it("finds records in the table with a complete reindex") {
    withLocalDynamoDbTable { table =>
      val records = createRecords(table, count = 2)

      val reindexParameters = CompleteReindexParameters(
        segment = 0,
        totalSegments = 1
      )

      val future = reader.findRecords[NamedRecord](
        reindexParameters, tableName = table.name
      )

      whenReady(future) {
        _ should contain theSameElementsAs records
      }
    }
  }

  it("finds records in the table with a maxResults reindex") {
    withLocalDynamoDbTable { table =>
      createRecords(table, count = 15)

      val reindexParameters = PartialReindexParameters(maxRecords = 5)

      val future = reader.findRecords[NamedRecord](
        reindexParameters, tableName = table.name
      )

      whenReady(future) {
        _ should have size 5
      }
    }
  }

  it("finds records in the table with a specified records reindex") {
    withLocalDynamoDbTable { table =>
      val records = createRecords(table, count = 15)

      val reindexParameters = SpecificReindexParameters(List(records.head.id))

      val future = reader.findRecords[NamedRecord](
        reindexParameters, tableName = table.name
      )

      whenReady(future) {
        _ shouldBe Seq(records.head)
      }
    }
  }

  it("fails if there's a DynamoDB error") {
    val table = Table("does-not-exist", "no-such-index")

    val reindexParameters = CompleteReindexParameters(
      segment = 5,
      totalSegments = 10
    )

    val future = reader.findRecords[NamedRecord](
      reindexParameters, tableName = table.name
    )

    whenReady(future.failed) {
      _ shouldBe a[ResourceNotFoundException]
    }
  }
}
