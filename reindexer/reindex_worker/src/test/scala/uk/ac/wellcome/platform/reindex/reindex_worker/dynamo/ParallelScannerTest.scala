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
      withParallelScanner { parallelScanner =>
        val records = createTableRecords(table, count = 1)

        val futureResult = parallelScanner.scan(
          segment = 0,
          totalSegments = 1
        )(table.name)

        whenReady(futureResult) { result =>
          result.map { fromJson[Record](_).get } shouldBe records
        }
      }
    }
  }

  it("reads all the records from a table across multiple scans") {
    runTest(totalRecords = 1000, segmentCount = 6)
  }

  it("reads all the records even when segmentCount > totalRecords") {
    runTest(totalRecords = 5, segmentCount = 10)
  }

  it(
    "returns a failed future if asked for a segment that's greater than totalSegments") {
    withLocalDynamoDbTable { table =>
      withParallelScanner { parallelScanner =>
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
  }

  private def runTest(totalRecords: Int, segmentCount: Int): Assertion = {
    withLocalDynamoDbTable { table =>
      withParallelScanner { parallelScanner =>
        val records = createTableRecords(table, count = totalRecords)

        // Note that segments are 0-indexed
        val futureResults = (0 until segmentCount).map { segment =>
          parallelScanner.scan(
            segment = segment,
            totalSegments = segmentCount
          )(table.name)
        }

        whenReady(Future.sequence(futureResults)) {
          actualRecords: Seq[List[String]] =>
            actualRecords.flatten.map { fromJson[Record](_).get } should contain theSameElementsAs records
        }
      }
    }
  }
}
