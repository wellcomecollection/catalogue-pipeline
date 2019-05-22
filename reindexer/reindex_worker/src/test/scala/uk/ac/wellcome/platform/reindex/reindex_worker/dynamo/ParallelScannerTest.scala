package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import com.gu.scanamo.Scanamo
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.DynamoFixtures
import uk.ac.wellcome.storage.fixtures.LocalDynamoDbVersioned

import scala.util.{Failure, Try}

class ParallelScannerTest
    extends FunSpec
    with Matchers
    with DynamoFixtures
    with LocalDynamoDbVersioned {

  it("reads a table with a single record") {
    withLocalDynamoDbTable { table =>
      withParallelScanner { parallelScanner =>
        val record = VersionedRecord(id = "123", data = "hello world", version = 1)
        Scanamo.put(dynamoDbClient)(table.name)(record)

        val result = parallelScanner.scan(
          segment = 0,
          totalSegments = 1
        )(table.name)

        parseRecords[VersionedRecord](result) shouldBe Seq(record)
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
        val result = parallelScanner.scan(
          segment = 10,
          totalSegments = 5
        )(table.name)

        result shouldBe a[Failure[_]]
        val err = result.failed.get

        err shouldBe a[AmazonDynamoDBException]
        err.getMessage should include(
          "Value '10' at 'segment' failed to satisfy constraint: Member must have value less than or equal to 4")
      }
    }
  }

  private def runTest(totalRecords: Int, segmentCount: Int): Assertion = {
    withLocalDynamoDbTable { table =>
      withParallelScanner { parallelScanner =>
        val records = (1 to totalRecords).map { id =>
          VersionedRecord(id = id.toString, data = "Hello world", version = 1)
        }

        records.map { record =>
          Scanamo.put(dynamoDbClient)(table.name)(record)
        }

        // Note that segments are 0-indexed
        val results: Seq[Try[Seq[String]]] = (0 until segmentCount).map { segment =>
          parallelScanner.scan(
            segment = segment,
            totalSegments = segmentCount
          )(table.name)
        }

        results.flatMap { parseRecords[VersionedRecord] } should contain theSameElementsAs records
      }
    }
  }
}
