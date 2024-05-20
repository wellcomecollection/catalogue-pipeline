package weco.pipeline.reindex_worker.fixtures

import org.scanamo.{Scanamo, Table => ScanamoTable}
import org.scanamo.generic.auto._
import weco.fixtures.RandomGenerators
import weco.storage.fixtures.DynamoFixtures.Table

import scala.language.higherKinds

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
    val records = (1 to count).map {
      _ =>
        createRecord()
    }

    records.foreach(
      record => {
        val scanamoTable = ScanamoTable[NamedRecord](table.name)
        Scanamo(dynamoClient).exec(scanamoTable.put(record))
      }
    )

    records
  }
}
