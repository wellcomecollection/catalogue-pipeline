package weco.pipeline.matcher.storage.dynamo

import org.scanamo.{DynamoFormat, Scanamo, Table}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.storage.dynamo.DynamoConfig

import scala.concurrent.{ExecutionContext, Future}

class DynamoBatchWriter[T](config: DynamoConfig)(
  implicit ec: ExecutionContext,
  client: DynamoDbClient,
  format: DynamoFormat[T]
) {
  private val scanamo = Scanamo(client)
  private val table = Table[T](config.tableName)

  def batchWrite(items: Seq[T]): Future[Unit] =
    Future {
      val ops = table.putAll(items.toSet)

      scanamo.exec(ops)
    }
}
