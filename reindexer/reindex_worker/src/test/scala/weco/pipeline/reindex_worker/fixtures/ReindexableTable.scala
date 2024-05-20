package weco.pipeline.reindex_worker.fixtures

import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import weco.storage.fixtures.DynamoFixtures
import weco.storage.fixtures.DynamoFixtures.Table

trait ReindexableTable extends DynamoFixtures {
  override def createTable(table: Table): Table =
    createTableWithHashKey(
      table,
      keyName = "id",
      keyType = ScalarAttributeType.S
    )
}
