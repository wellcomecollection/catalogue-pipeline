package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import org.scanamo.syntax._
import org.scanamo.{DynamoFormat, Scanamo, Table}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.{ExecutionContext, Future}

/** Fetches multiple IDs from DynamoDB.
  *
  * This uses the BatchGet API, see
  * https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_BatchGetItem.html
  *
  * If an ID doesn't exist, then no item is returned.  It returns up
  * to 100 items or 16 MB of data, whichever comes first.
  */
class MultiItemGetter(implicit
                      val dynamoClient: DynamoDbClient,
                      val ec: ExecutionContext) {

  def get[T](ids: Seq[String], partitionKey: String = "id")(tableName: String)(
    implicit format: DynamoFormat[T]
  ): Future[Seq[T]] = {
    val table = Table[T](tableName)

    val ops = table.getAll(partitionKey in ids.toSet)

    Future {
      val result = Scanamo(dynamoClient).exec(ops)

      val successes = result.collect { case Right(t) => t }
      val failures = result.collect { case Left(err) => err.toString }

      if (failures.isEmpty) {
        successes.toSeq
      } else {
        throw new Throwable(
          s"Errors parsing Scanamo result: ${failures.mkString(", ")}")
      }
    }
  }
}
