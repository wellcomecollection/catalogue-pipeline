package weco.pipeline.reindex_worker.dynamo

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scanamo.generic.auto._
import weco.pipeline.reindex_worker.fixtures.ReindexDynamoFixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.higherKinds

class MaxRecordsScannerTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with ReindexDynamoFixtures {

  val scanner = new MaxRecordsScanner()

  it("reads a table with a single record") {
    withLocalDynamoDbTable {
      table =>
        val records = createRecords(table, count = 1)

        val future = scanner.scan[NamedRecord](maxRecords = 1)(table.name)

        whenReady(future) {
          _ shouldBe records
        }
    }
  }

  it("handles being asked for more records than are in the table") {
    withLocalDynamoDbTable {
      table =>
        val records = createRecords(table, count = 5)

        val future = scanner.scan[NamedRecord](maxRecords = 10)(table.name)

        whenReady(future) {
          _ should contain theSameElementsAs records
        }
    }
  }

  it("only returns as many records as were asked for") {
    withLocalDynamoDbTable {
      table =>
        createRecords(table, count = 5)

        val future = scanner.scan[NamedRecord](maxRecords = 3)(table.name)

        whenReady(future) {
          _ should have size 3
        }
    }
  }

  it("fails if the data is in the wrong format") {
    case class NumberedRecord(id: Int, text: String)

    withLocalDynamoDbTable {
      table =>
        createRecords(table, count = 5)

        val future = scanner.scan[NumberedRecord](maxRecords = 3)(table.name)

        whenReady(future.failed) {
          err =>
            err shouldBe a[RuntimeException]
            err.getMessage should startWith("Errors parsing Scanamo result")
        }
    }
  }
}
