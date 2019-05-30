package uk.ac.wellcome.platform.reindex.reindex_worker.fixtures

import com.gu.scanamo.Scanamo
import uk.ac.wellcome.platform.reindex.reindex_worker.dynamo.{MaxRecordsScanner, ParallelScanner, ScanSpecScanner}
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

trait DynamoFixtures extends LocalDynamoDb {
  override def createTable(table: Table): Table =
    createTableWithHashKey(table, keyName = "id")

  case class Record(id: String, data: String)

  def createRecords(count: Int = 4): Seq[Record] =
    (1 to count).map { i =>
      Record(
        id = s"id$i",
        data = Random.alphanumeric.take(i) mkString
      )
    }

  def createTableRecords(table: Table, count: Int = 4): Seq[Record] = {
    val records = createRecords(count)
    records.foreach { r =>
      Scanamo.put(dynamoDbClient)(table.name)(r)
    }
    records
  }

  def withScanSpecScanner[R](testWith: TestWith[ScanSpecScanner, R]): R = {
    val scanner = new ScanSpecScanner(dynamoDbClient)

    testWith(scanner)
  }

  def withParallelScanner[R](testWith: TestWith[ParallelScanner, R]): R =
    withScanSpecScanner { scanSpecScanner =>
      val scanner = new ParallelScanner(scanSpecScanner = scanSpecScanner)

      testWith(scanner)
    }

  def withMaxRecordsScanner[R](testWith: TestWith[MaxRecordsScanner, R]): R =
    withScanSpecScanner { scanSpecScanner =>
      val scanner = new MaxRecordsScanner(scanSpecScanner = scanSpecScanner)

      testWith(scanner)
    }
}
