package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.auto._
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.ReindexDynamoFixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NewParallelScannerTest
  extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with ReindexDynamoFixtures {

  val scanner = new NewParallelScanner()

  it("reads a table with a single record") {
    withLocalDynamoDbTable { table =>
      val records = createRecords(table, count = 1)

      val future = scanner.scan[NamedRecord](
        segment = 0,
        totalSegments = 1
      )(table.name)

      whenReady(future) {
        _ shouldBe records
      }
    }
  }

  it("reads all the records from a table across multiple scans") {
    runTest(recordCount = 1000, segmentCount = 6)
  }

  it("reads all the records even when segmentCount > totalRecords") {
    runTest(recordCount = 5, segmentCount = 10)
  }

  it("fails if asked for a segment that's greater than totalSegments") {
    withLocalDynamoDbTable { table =>
      val future = scanner.scan[NamedRecord](
        segment = 10,
        totalSegments = 5
      )(table.name)

      whenReady(future.failed) { err =>
        err shouldBe a[AmazonDynamoDBException]
        val message = err.asInstanceOf[AmazonDynamoDBException].getMessage
        message should include(
          "Value '10' at 'segment' failed to satisfy constraint: Member must have value less than or equal to 4")
      }
    }
  }

  it("fails if the data is in the wrong format") {
    case class NumberedRecord(id: Int, text: String)

    withLocalDynamoDbTable { table =>
      createRecords(table, count = 10)

      val future = scanner.scan[NumberedRecord](
        segment = 1,
        totalSegments = 5
      )(table.name)

      whenReady(future.failed) { err =>
        err shouldBe a[RuntimeException]
        err.getMessage should startWith("Errors parsing Scanamo result")
      }
    }
  }

  private def runTest(recordCount: Int, segmentCount: Int): Assertion = {
    withLocalDynamoDbTable { table =>
      val records = createRecords(table, count = recordCount)

      // Note that segments are 0-indexed
      val futures = (0 until segmentCount).map { segment =>
        scanner.scan[NamedRecord](
          segment = segment,
          totalSegments = segmentCount
        )(table.name)
      }

      whenReady(Future.sequence(futures)) {
        _.flatten should contain theSameElementsAs records
      }
    }
  }
}
