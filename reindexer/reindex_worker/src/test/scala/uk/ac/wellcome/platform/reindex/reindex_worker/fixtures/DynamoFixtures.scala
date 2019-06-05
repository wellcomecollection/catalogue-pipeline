package uk.ac.wellcome.platform.reindex.reindex_worker.fixtures

import com.gu.scanamo.Scanamo
import uk.ac.wellcome.platform.reindex.reindex_worker.dynamo.{MaxRecordsScanner, ParallelScanner, ScanSpecScanner}
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

trait DynamoFixtures extends ReindexableTable {
  case class NamedRecord(
    id: String,
    name: String
  )

  private def createRecord(): NamedRecord = NamedRecord(
    id = Random.alphanumeric.take(5) mkString,
    name = Random.alphanumeric.take(15) mkString
  )

  def createRecords(table: Table, count: Int): Seq[NamedRecord] = {
    val records = (1 to count).map { _ => createRecord() }

    records.foreach(record =>
      Scanamo.put(dynamoDbClient)(table.name)(record))

    records
  }

  def createScanSpecScanner: ScanSpecScanner =
    new ScanSpecScanner(dynamoDbClient)

  def createParallelScanner: ParallelScanner =
    new ParallelScanner(
      scanSpecScanner = createScanSpecScanner
    )

  def createMaxRecordsScanner: MaxRecordsScanner =
    new MaxRecordsScanner(
      scanSpecScanner = createScanSpecScanner
    )
}
