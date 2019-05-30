package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.DynamoFixtures

class MaxRecordsScannerTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with DynamoFixtures {

  it("reads a table with a single record") {
    withLocalDynamoDbTable { table =>
      withMaxRecordsScanner { maxResultScanner =>
        val records = createTableRecords(table, count = 1)

        val futureResult = maxResultScanner.scan(maxRecords = 1)(table.name)

        whenReady(futureResult) { result =>
          result.map { fromJson[Record](_).get } shouldBe records
        }
      }
    }
  }

  it("handles being asked for more records than are in the table") {
    withLocalDynamoDbTable { table =>
      withMaxRecordsScanner { maxResultScanner =>
        val records = createTableRecords(table, count = 5)

        val futureResult = maxResultScanner.scan(maxRecords = 10)(table.name)

        whenReady(futureResult) { result =>
          result.map { fromJson[Record](_).get } should contain theSameElementsAs records
        }
      }
    }
  }
}
