package weco.pipeline.reindex_worker.dynamo

import grizzled.slf4j.Logging
import org.scanamo.syntax._
import org.scanamo.{DynamoFormat, Scanamo, Table}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.{ExecutionContext, Future}

/** Fetches multiple IDs from DynamoDB.
  *
  * This uses the BatchGet API, see
  * https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_BatchGetItem.html
  *
  * If an ID doesn't exist, then no item is returned. It returns up to 100 items
  * or 16 MB of data, whichever comes first.
  */
class MultiItemGetter(
  implicit val dynamoClient: DynamoDbClient,
  val ec: ExecutionContext
) extends Logging {

  def get[T](ids: Seq[String], partitionKey: String = "id")(tableName: String)(
    implicit format: DynamoFormat[T]
  ): Future[Seq[T]] = {
    val table = Table[T](tableName)

    val ops = table.getAll(partitionKey in ids.toSet)

    Future {
      val result = Scanamo(dynamoClient).exec(ops)

      val successes = result.collect { case Right(t) => t }
      val failures = result.collect { case Left(err) => err.toString }

      // This usually means somebody has asked us to reindex the wrong IDs.
      //
      // e.g. trying to reindex the Sierra b-number with a check digit and
      // prefix (b32496485) instead of the unprefixed ID in the store (3249648).
      //
      // It doesn't prevent the reindexer from potentially sending other IDs
      // in the batch, but drop a warning to help them realise their mistake.
      if (successes.size != ids.size) {
        warn(s"Looked in table $tableName; could not find all the IDs in $ids")
      }

      if (failures.isEmpty) {
        successes.toSeq
      } else {
        throw new Throwable(
          s"Errors parsing Scanamo result: ${failures.mkString(", ")}"
        )
      }
    }
  }
}
