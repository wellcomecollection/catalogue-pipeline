package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

class BatchItemGetter(dynamoDBClient: AmazonDynamoDB)(
  implicit ec: ExecutionContext) {

  final val PARTITION_KEY: String = "id"
  val dynamoDB = new DynamoDB(dynamoDBClient)

  def get(recordIds: List[String])(tableName: String): Future[List[String]] =
    for {
      batchGetResult: BatchGetItemOutcome <- Future {
        dynamoDB.batchGetItem(
          new TableKeysAndAttributes(tableName)
            .addHashOnlyPrimaryKeys(PARTITION_KEY, recordIds: _*))
      }
      tableItems: List[Item] = batchGetResult.getTableItems.asScala
        .get(tableName)
        .map { _.asScala.toList }
        .getOrElse(List())
      jsonStrings = tableItems.map { _.toJSON }
    } yield jsonStrings
}
