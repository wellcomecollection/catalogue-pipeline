package weco.pipeline.reindex_worker.dynamo

import org.scanamo.DynamoFormat
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{
  ScanRequest,
  ScanResponse
}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/** Implements a wrapper for DynamoDB Scan operations using a ScanSpec.
  *
  * This wrapper provides a list of JSON strings, which can be sent directly to
  * a downstream application.
  *
  * This is based on https://alexwlchan.net/2018/08/parallel-scan-scanamo/,
  * although the APIs have changed slightly since that was written.
  */
trait ScanRequestScanner extends ItemParser {
  implicit val dynamoClient: DynamoDbClient
  implicit val ec: ExecutionContext

  protected def scan[T](
    request: ScanRequest
  )(implicit format: DynamoFormat[T]): Future[Seq[T]] =
    for {
      response: ScanResponse <- Future {
        dynamoClient.scan(request).asInstanceOf[ScanResponse]
      }
      items = response.items().asScala
      result <- parseItems[T](items)
    } yield result
}
