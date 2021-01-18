package uk.ac.wellcome.platform.matcher.fixtures

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._
import uk.ac.wellcome.fixtures.RandomGenerators
import uk.ac.wellcome.storage.fixtures.DynamoFixtures
import uk.ac.wellcome.storage.fixtures.DynamoFixtures.Table

trait LocalWorkGraphDynamoDb extends DynamoFixtures with RandomGenerators {
  override def createTable(table: Table): Table = Table("table", "index")

  def createWorkGraphTable(dynamoClient: DynamoDbClient): Table = {
    val tableName = s"table-${randomAlphanumeric()}"
    val indexName = s"index-${randomAlphanumeric()}"
    val table = Table(tableName, indexName)

    createTableFromRequest(
      table,
      CreateTableRequest.builder()
        .tableName(table.name)
        .keySchema(
          KeySchemaElement.builder()
            .attributeName("id")
            .keyType(KeyType.HASH)
            .build()
        )
        .attributeDefinitions(
          AttributeDefinition.builder()
            .attributeName("id")
            .attributeType("S")
            .build(),
          AttributeDefinition.builder()
            .attributeName("componentId")
            .attributeType("S")
            .build()
        )
        .globalSecondaryIndexes(
          GlobalSecondaryIndex.builder()
            .indexName(table.index)
            .projection(
              Projection.builder()
                .projectionType(ProjectionType.ALL)
                .build()
            )
            .keySchema(
              KeySchemaElement.builder()
                .attributeName("componentId")
                .keyType(KeyType.HASH)
                .build()
            )
            .provisionedThroughput(
              ProvisionedThroughput.builder()
                .readCapacityUnits(1L)
                .writeCapacityUnits(1L)
                .build()
            )
            .build()
        )
    )
  }
}
