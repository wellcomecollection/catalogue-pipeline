package weco.pipeline.reindex_worker.services

import grizzled.slf4j.Logging
import org.scanamo.DynamoFormat
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import weco.pipeline.reindex_worker.dynamo.{MaxRecordsScanner, MultiItemGetter, ParallelScanner}
import weco.pipeline.reindex_worker.models.{
  CompleteReindexParameters,
  PartialReindexParameters,
  ReindexParameters,
  SpecificReindexParameters
}

import scala.concurrent.{ExecutionContext, Future}

class RecordReader(implicit dynamoClient: DynamoDbClient, ec: ExecutionContext) extends Logging {

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
