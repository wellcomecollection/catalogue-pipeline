package weco.pipeline.reindex_worker.dynamo

import org.scanamo.DynamoFormat
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ScanRequest

import scala.concurrent.{ExecutionContext, Future}

/** Fetch at most `maxRecords` values from the table. */
class MaxRecordsScanner(
  implicit val dynamoClient: DynamoDbClient,
  val ec: ExecutionContext
) extends ScanRequestScanner {

  def scan[T](
    maxRecords: Int
  )(tableName: String)(implicit format: DynamoFormat[T]): Future[Seq[T]] = {
    val request = ScanRequest
      .builder()
      .tableName(tableName)
      .limit(maxRecords)
      .build()

    scan(request)
  }
}
