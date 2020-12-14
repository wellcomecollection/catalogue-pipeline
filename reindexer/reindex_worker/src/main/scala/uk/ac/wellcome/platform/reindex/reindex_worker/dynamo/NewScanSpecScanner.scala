package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document._
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import org.scanamo.DynamoFormat

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/** Implements a wrapper for DynamoDB Scan operations using a ScanSpec.
 *
 * This wrapper provides a list of JSON strings, which can be sent directly
 * to a downstream application.
 *
 * For the options allowed by ScanSpec, see:
 * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/document/spec/ScanSpec.html
 *
 * This is based on https://alexwlchan.net/2018/08/parallel-scan-scanamo/, although
 * the APIs have changed slightly since that was written.
 */
trait NewScanSpecScanner extends ItemParser {
  implicit val dynamoClient: AmazonDynamoDB
  implicit val ec: ExecutionContext

  private val documentApiClient = new DynamoDB(dynamoClient)

  protected def scan[T](spec: ScanSpec)(tableName: String)(implicit format: DynamoFormat[T]): Future[Seq[T]] = {
    val table = documentApiClient.getTable(tableName)

    for {
      items: Seq[Item] <- Future { table.scan(spec).asScala.toSeq }
      result <- parseItems[T](items)
    } yield result
  }
}
