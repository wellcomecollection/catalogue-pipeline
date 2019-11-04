package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/** Wraps DynamoDB in order to run a DynamoDB BatchGet for a given list of
  * item IDs (= hash keys = partition keys) in a given table.
  *
  * https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_BatchGetItem.html
  *
  * If an ID doesn't exist then no corresponding item is returned in the list.
  * Returns up to 100 items or 16 MB of data (whichever is met first)
  */
class BatchItemGetter(dynamoDBClient: AmazonDynamoDB)(
  implicit ec: ExecutionContext) {

  private final val PARTITION_KEY: String = "id"
  val dynamoDB = new DynamoDB(dynamoDBClient)

  def get(itemIds: List[String])(tableName: String): Future[List[String]] =
    for {
      batchGetResult: BatchGetItemOutcome <- Future {
        dynamoDB.batchGetItem(
          new TableKeysAndAttributes(tableName)
            .addHashOnlyPrimaryKeys(PARTITION_KEY, itemIds: _*))
      }
      tableItems: List[Item] = batchGetResult.getTableItems.asScala
        .get(tableName)
        .map { _.asScala.toList }
        .getOrElse(List())
      jsonStrings = tableItems.map { _.toJSON }
    } yield jsonStrings
}
