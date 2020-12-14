package uk.ac.wellcome.platform.reindex.reindex_worker.services

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import grizzled.slf4j.Logging
import org.scanamo.DynamoFormat
import uk.ac.wellcome.platform.reindex.reindex_worker.dynamo.{
  BatchItemGetter,
  MaxRecordsScanner,
  MultiItemGetter,
  NewMaxRecordsScanner,
  NewParallelScanner,
  ParallelScanner
}
import uk.ac.wellcome.platform.reindex.reindex_worker.models._
import uk.ac.wellcome.storage.dynamo.DynamoConfig

import scala.concurrent.{ExecutionContext, Future}

class NewRecordReader(
  implicit
  dynamoClient: AmazonDynamoDB,
  ec: ExecutionContext
) extends Logging {

  private val maxRecordsScanner = new NewMaxRecordsScanner()
  private val parallelScanner = new NewParallelScanner()
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

class RecordReader(
  maxRecordsScanner: MaxRecordsScanner,
  parallelScanner: ParallelScanner,
  specificItemsGetter: BatchItemGetter
) extends Logging {

  def findRecordsForReindexing(
    reindexParameters: ReindexParameters,
    dynamoConfig: DynamoConfig): Future[List[String]] = {
    debug(s"Finding records that need reindexing for $reindexParameters")

    reindexParameters match {
      case CompleteReindexParameters(segment, totalSegments) =>
        parallelScanner
          .scan(
            segment = segment,
            totalSegments = totalSegments
          )(tableName = dynamoConfig.tableName)
      case PartialReindexParameters(maxRecords) =>
        maxRecordsScanner.scan(maxRecords = maxRecords)(
          tableName = dynamoConfig.tableName)
      case SpecificReindexParameters(ids) =>
        specificItemsGetter.get(ids)(tableName = dynamoConfig.tableName)
    }
  }
}
