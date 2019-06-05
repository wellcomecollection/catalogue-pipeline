package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.DynamoFixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ParallelScannerTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with DynamoFixtures {

  it("reads a table with a single record") {
    withLocalDynamoDbTable { table =>
      val parallelScanner = createParallelScanner

      val records = createRecords(table, count = 1)

      val futureResult = parallelScanner.scan(
        segment = 0,
        totalSegments = 1
      )(table.name)

      whenReady(futureResult) { result =>
        result.map { fromJson[NamedRecord](_).get } shouldBe records
      }
    }
  }

  it("reads all the records from a table across multiple scans") {
    runTest(recordCount = 1000, segmentCount = 6)
  }

  it("reads all the records even when segmentCount > totalRecords") {
    runTest(recordCount = 5, segmentCount = 10)
  }

  it(
    "returns a failed future if asked for a segment that's greater than totalSegments") {
    withLocalDynamoDbTable { table =>
      val parallelScanner = createParallelScanner

      val future = parallelScanner.scan(
        segment = 10,
        totalSegments = 5
      )(table.name)

      whenReady(future.failed) { r =>
        r shouldBe a[AmazonDynamoDBException]
        val message = r.asInstanceOf[AmazonDynamoDBException].getMessage
        message should include(
          "Value '10' at 'segment' failed to satisfy constraint: Member must have value less than or equal to 4")
      }
    }
  }

  private def runTest(recordCount: Int, segmentCount: Int): Assertion = {
    withLocalDynamoDbTable { table =>
      val parallelScanner = createParallelScanner

      val records = createRecords(table, count = recordCount)

      // Note that segments are 0-indexed
      val futureResults = (0 until segmentCount).map { segment =>
        parallelScanner.scan(
          segment = segment,
          totalSegments = segmentCount
        )(table.name)
      }

      whenReady(Future.sequence(futureResults)) {
        actualRecords: Seq[List[String]] =>
          actualRecords.flatten.map { fromJson[NamedRecord](_).get } should contain theSameElementsAs records

      }
    }
  }
}
