package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import org.scanamo.DynamoFormat

import scala.concurrent.{ExecutionContext, Future}

class MaxRecordsScanner(scanSpecScanner: ScanSpecScanner) {

  /** Run a DynamoDB Scan that returns at most `maxResults` values.
    *
    * It may return less if there aren't enough results in the table, or if
    * `maxResults` is larger than the maximum page size.
    */
  def scan(maxRecords: Int)(tableName: String): Future[List[String]] = {

    val scanSpec = new ScanSpec()
      .withMaxResultSize(maxRecords)

    scanSpecScanner.scan(scanSpec = scanSpec, tableName = tableName)
  }
}

/** Fetch at most `maxRecords` values from the table. */
class NewMaxRecordsScanner(
  implicit
  val dynamoClient: AmazonDynamoDB,
  val ec: ExecutionContext) extends NewScanSpecScanner {

  def scan[T](maxRecords: Int)(tableName: String)(implicit format: DynamoFormat[T]): Future[Seq[T]] = {
    val spec = new ScanSpec().withMaxResultSize(maxRecords)

    scan(spec)(tableName)
  }
}
