package uk.ac.wellcome.platform.reindex.reindex_worker.fixtures

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table

trait ReindexableTable extends LocalDynamoDb {
  override def createTable(table: Table): Table =
    createTableWithHashKey(table, keyName = "id", keyType = ScalarAttributeType.S)
}
