package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.{
  BatchGetItemOutcome,
  DynamoDB,
  Item,
  TableKeysAndAttributes
}
import org.scanamo.DynamoFormat

import scala.collection.JavaConverters._
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
                      val dynamoClient: AmazonDynamoDB,
                      val ec: ExecutionContext)
    extends ItemParser {

  private val documentApiClient = new DynamoDB(dynamoClient)

  def get[T](ids: Seq[String], partitionKey: String = "id")(tableName: String)(
    implicit format: DynamoFormat[T]
  ): Future[Seq[T]] = {
    val batchGetRequest: TableKeysAndAttributes =
      new TableKeysAndAttributes(tableName)
        .addHashOnlyPrimaryKeys(partitionKey, ids: _*)

    for {
      batchGetResult: BatchGetItemOutcome <- Future {
        documentApiClient.batchGetItem(batchGetRequest)
      }

      items = batchGetResult.getTableItems.asScala.get(tableName) match {
        case Some(it) => it.asScala
        case None     => Seq[Item]()
      }

      result <- parseItems[T](items)
    } yield result
  }
}
