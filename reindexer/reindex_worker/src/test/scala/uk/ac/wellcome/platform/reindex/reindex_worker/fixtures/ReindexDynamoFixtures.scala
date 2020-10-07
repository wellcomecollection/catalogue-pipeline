package uk.ac.wellcome.platform.reindex.reindex_worker.fixtures

import org.scanamo.{Scanamo, Table => ScanamoTable}
import org.scanamo.auto._
import uk.ac.wellcome.fixtures.RandomGenerators
import uk.ac.wellcome.platform.reindex.reindex_worker.dynamo.{BatchItemGetter, MaxRecordsScanner, ParallelScanner, ScanSpecScanner}
import uk.ac.wellcome.storage.fixtures.DynamoFixtures.Table

import scala.concurrent.ExecutionContext.Implicits.global

trait ReindexDynamoFixtures extends ReindexableTable with RandomGenerators {
  case class NamedRecord(
    id: String,
    name: String
  )

  private def createRecord(): NamedRecord = NamedRecord(
    id = randomAlphanumeric(length = 5),
    name = randomAlphanumeric(length = 15)
  )

  def createRecords(table: Table, count: Int): Seq[NamedRecord] = {
    val records = (1 to count).map { _ =>
      createRecord()
    }

    records.foreach(record => {
      val scanamoTable = ScanamoTable[NamedRecord](table.name)
      Scanamo(dynamoClient).exec(scanamoTable.put(record))
    })

    records
  }

  def createScanSpecScanner: ScanSpecScanner =
    new ScanSpecScanner(dynamoClient)

  def createParallelScanner: ParallelScanner =
    new ParallelScanner(
      scanSpecScanner = createScanSpecScanner
    )

  def createMaxRecordsScanner: MaxRecordsScanner =
    new MaxRecordsScanner(
      scanSpecScanner = createScanSpecScanner
    )

  def createBatchItemGetter: BatchItemGetter =
    new BatchItemGetter(dynamoClient)
}
