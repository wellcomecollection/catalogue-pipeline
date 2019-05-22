package uk.ac.wellcome.platform.reindex.reindex_worker.dynamo

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.document._
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec

import scala.collection.JavaConverters._
import scala.util.Try

/** Implements a wrapper for DynamoDB Scan operations using a ScanSpec.
  *
  * This wrapper provides a list of JSON strings, which can be sent directly
  * to a downstream application.
  *
  * For the options allowed by ScanSpec, see:
  * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/document/spec/ScanSpec.html
  */
class ScanSpecScanner(dynamoDBClient: AmazonDynamoDB) {

  val dynamoDB = new DynamoDB(dynamoDBClient)

  /** Run a Scan specified by a ScanSpec.
    *
    * Note that this returns a Future[List], so results will be cached in-memory.
    * Design your spec accordingly.
    */
  def scan(scanSpec: ScanSpec, tableName: String): Try[Seq[String]] = {
    for {
      table <- Try { dynamoDB.getTable(tableName) }
      scanResult: ItemCollection[ScanOutcome] <- Try { table.scan(scanSpec) }
      items: List[Item] = scanResult.asScala.toList
      jsonStrings = items.map { _.toJSON }
    } yield jsonStrings
  }
}
