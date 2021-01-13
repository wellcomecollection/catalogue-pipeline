package uk.ac.wellcome.platform.matcher.storage.dynamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import org.scanamo.{DynamoFormat, Scanamo, Table}
import uk.ac.wellcome.storage.dynamo.DynamoConfig

import scala.concurrent.{ExecutionContext, Future}

class DynamoBatchWriter[T](config: DynamoConfig)(
  implicit ec: ExecutionContext,
  client: AmazonDynamoDB,
  format: DynamoFormat[T]
) {
  private val scanamo = Scanamo(client)
  private val table = Table[T](config.tableName)

  def batchWrite(items: Seq[T]): Future[Unit] =
    Future {
      // We can write up to 25 nodes at once as part of a DynamoDB BatchWriteItem
      // operation.  Since nodes are small, we expect this not to exceed the
      // 16MB total limit / 400KB limit for a single node.
      items.grouped(25)
        .flatMap { batch =>
          val ops = table.putAll(batch.toSet)
          scanamo.exec(ops)
        }
        .foreach { result =>

          // Note: this is based on a description of how BatchWriteItems works, and
          // isn't tested.  Unfortunately, the local DynamoDB instance we use ignores
          // provisioned throughput settings, so we can't test what happens if we
          // write too quickly.
          // See https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.UsageNotes.html#DynamoDBLocal.Differences
          if (result.getUnprocessedItems.isEmpty) {
            ()
          } else {
            throw new Throwable(s"Not all items were written correctly: ${result.getUnprocessedItems}")
          }
        }
    }
}
