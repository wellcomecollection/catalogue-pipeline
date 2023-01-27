package weco.pipeline.reindex_worker.dynamo

import org.scanamo.DynamoFormat
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ScanRequest

import scala.concurrent.{ExecutionContext, Future}

/** Implements a wrapper for Parallel Scans of a DynamoDB table. In a nutshell,
  * this operation lets you have multiple parallel workers that Scan the rows of
  * a DynamoDB table, and DynamoDB handles the problem of dividing up rows
  * between the different workers.
  *
  * https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Scan.html#Scan.ParallelScan
  */
class ParallelScanner(
  implicit val dynamoClient: DynamoDbClient,
  val ec: ExecutionContext
) extends ScanRequestScanner {

  /** Run a Parallel Scan for a single worker.
    *
    * To perform a parallel scan, each worker should call this method with two
    * parameters:
    *
    * @param segment
    *   Which segment this work is scanning. Each worker should choose a
    *   different segment. This parameter is 0-indexed.
    * @param totalSegments
    *   How many segments there are in total. Each worker should use the same
    *   value.
    *
    * Note that this returns a Future[List], so results will be cached
    * in-memory. Choose segment count accordingly.
    */
  def scan[T](segment: Int, totalSegments: Int)(
    tableName: String
  )(implicit dynamoFormat: DynamoFormat[T]): Future[Seq[T]] = {

    // Create the ScanSpec configuration and the DynamoDB table.  This is
    // based on the Java example of a Parallel Scan from the AWS docs:
    // https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/ScanJavaDocumentAPI.html
    //
    val request = ScanRequest
      .builder()
      .tableName(tableName)
      .totalSegments(totalSegments)
      .segment(segment)
      .build()

    scan(request)
  }
}
