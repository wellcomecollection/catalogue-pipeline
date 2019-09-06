package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.reindex.reindex_worker.fixtures.ReindexDynamoFixtures

class MaxRecordsScannerTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with ReindexDynamoFixtures {

  it("reads a table with a single record") {
    withLocalDynamoDbTable { table =>
      val maxRecordsScanner = createMaxRecordsScanner

      val records = createRecords(table, count = 1)

      val futureResult = maxRecordsScanner.scan(maxRecords = 1)(table.name)

      whenReady(futureResult) { result =>
        result.map { fromJson[NamedRecord](_).get } shouldBe records
      }
    }
  }

  it("handles being asked for more records than are in the table") {
    withLocalDynamoDbTable { table =>
      val maxRecordsScanner = createMaxRecordsScanner

      val records = createRecords(table, count = 5)

      val futureResult = maxRecordsScanner.scan(maxRecords = 10)(table.name)

      whenReady(futureResult) { result =>
        result.map { fromJson[NamedRecord](_).get } should contain theSameElementsAs records
      }
    }
  }

  it("only returns the number of records asked for") {
    withLocalDynamoDbTable { table =>
      val maxRecordsScanner = createMaxRecordsScanner

      createRecords(table, count = 5)

      val futureResult = maxRecordsScanner.scan(maxRecords = 3)(table.name)

      whenReady(futureResult) {
        _ should have size 3
      }
    }
  }
}
