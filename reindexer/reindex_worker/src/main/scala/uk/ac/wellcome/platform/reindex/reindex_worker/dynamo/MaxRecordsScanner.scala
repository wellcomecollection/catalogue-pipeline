package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import org.scanamo.DynamoFormat

import scala.concurrent.{ExecutionContext, Future}

/** Fetch at most `maxRecords` values from the table. */
class MaxRecordsScanner(
  implicit
  val dynamoClient: AmazonDynamoDB,
  val ec: ExecutionContext) extends ScanSpecScanner {

  def scan[T](maxRecords: Int)(tableName: String)(implicit format: DynamoFormat[T]): Future[Seq[T]] = {
    val spec = new ScanSpec().withMaxResultSize(maxRecords)

    scan(spec)(tableName)
  }
}
