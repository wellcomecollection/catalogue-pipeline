package uk.ac.wellcome.platform.reindex.reindex_worker.fixtures

import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import uk.ac.wellcome.storage.fixtures.DynamoFixtures
import uk.ac.wellcome.storage.fixtures.DynamoFixtures.Table

trait ReindexableTable extends DynamoFixtures {
  override def createTable(table: Table): Table =
    createTableWithHashKey(
      table,
      keyName = "id",
      keyType = ScalarAttributeType.S)
}
