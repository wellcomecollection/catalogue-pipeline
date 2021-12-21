package weco.pipeline.matcher.fixtures

import software.amazon.awssdk.services.dynamodb.model._
import weco.fixtures.RandomGenerators
import weco.storage.fixtures.DynamoFixtures
import weco.storage.fixtures.DynamoFixtures.Table

trait LocalWorkGraphDynamoDb extends DynamoFixtures with RandomGenerators {
  override def createTable(table: Table): Table = Table("table", "index")

  def createWorkGraphTable(table: Table): Table =
    createTableFromRequest(
      table,
      CreateTableRequest
        .builder()
        .tableName(table.name)
        .keySchema(
          KeySchemaElement
            .builder()
            .attributeName("id")
            .keyType(KeyType.HASH)
            .build()
        )
        .attributeDefinitions(
          AttributeDefinition
            .builder()
            .attributeName("id")
            .attributeType("S")
            .build(),
          AttributeDefinition
            .builder()
            .attributeName("subgraphId")
            .attributeType("S")
            .build()
        )
        .globalSecondaryIndexes(
          GlobalSecondaryIndex
            .builder()
            .indexName(table.index)
            .projection(
              Projection
                .builder()
                .projectionType(ProjectionType.ALL)
                .build()
            )
            .keySchema(
              KeySchemaElement
                .builder()
                .attributeName("subgraphId")
                .keyType(KeyType.HASH)
                .build()
            )
            .provisionedThroughput(
              ProvisionedThroughput
                .builder()
                .readCapacityUnits(1L)
                .writeCapacityUnits(1L)
                .build()
            )
            .build()
        )
    )
}
