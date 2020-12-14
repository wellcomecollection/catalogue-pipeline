package uk.ac.wellcome.platform.reindex.reindex_worker.services

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import grizzled.slf4j.Logging
import org.scanamo.DynamoFormat
import uk.ac.wellcome.platform.reindex.reindex_worker.dynamo.{
  MultiItemGetter,
  MaxRecordsScanner,
  ParallelScanner
}
import uk.ac.wellcome.platform.reindex.reindex_worker.models._

import scala.concurrent.{ExecutionContext, Future}

class RecordReader(
  implicit
  dynamoClient: AmazonDynamoDB,
  ec: ExecutionContext
) extends Logging {

  private val maxRecordsScanner = new MaxRecordsScanner()
  private val parallelScanner = new ParallelScanner()
  private val multiItemGetter = new MultiItemGetter()

  def findRecords[T](
    reindexParameters: ReindexParameters,
    tableName: String
  )(implicit dynamoFormat: DynamoFormat[T]): Future[Seq[T]] = {
    debug(s"Finding records that need reindexing for $reindexParameters")

    reindexParameters match {
      case CompleteReindexParameters(segment, totalSegments) =>
        parallelScanner.scan(segment, totalSegments)(tableName)

      case PartialReindexParameters(maxRecords) =>
        maxRecordsScanner.scan(maxRecords = maxRecords)(tableName)

      case SpecificReindexParameters(ids) =>
        multiItemGetter.get(ids)(tableName)
    }
  }
}
