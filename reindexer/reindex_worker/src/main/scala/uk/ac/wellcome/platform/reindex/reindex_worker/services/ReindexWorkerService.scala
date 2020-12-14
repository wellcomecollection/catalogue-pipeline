package uk.ac.wellcome.platform.reindex.reindex_worker.services

import akka.Done
import org.scanamo.auto._
import org.scanamo.time.JavaTimeFormats._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.platform.reindex.reindex_worker.models.source.{
  CalmReindexPayload,
  MetsReindexPayload,
  MiroInventoryReindexPayload,
  MiroReindexPayload,
  ReindexPayload,
  SierraReindexPayload
}
import uk.ac.wellcome.platform.reindex.reindex_worker.models.{
  ReindexJobConfig,
  ReindexParameters,
  ReindexRequest,
  ReindexSource
}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class ReindexWorkerService[Destination](
  recordReader: RecordReader,
  bulkMessageSender: BulkMessageSender[Destination],
  sqsStream: SQSStream[NotificationMessage],
  reindexJobConfigMap: Map[String, ReindexJobConfig[Destination]]
)(implicit ec: ExecutionContext)
    extends Runnable {

  private def processMessage(message: NotificationMessage): Future[Unit] =
    for {
      reindexRequest <- Future.fromTry {
        fromJson[ReindexRequest](message.body)
      }

      reindexJobConfig = reindexJobConfigMap.getOrElse(
        reindexRequest.jobConfigId,
        throw new RuntimeException(
          s"No such job config: ${reindexRequest.jobConfigId}")
      )

      records <- readRecords(
        source = reindexJobConfig.source,
        reindexParameters = reindexRequest.parameters,
        tableName = reindexJobConfig.dynamoConfig.tableName
      )

      sourcePayloads = records.map { _.toSourcePayload }

      _ <- bulkMessageSender.send(
        sourcePayloads,
        destination = reindexJobConfig.destinationConfig
      )
    } yield ()

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)

  private def readRecords(source: ReindexSource,
                          reindexParameters: ReindexParameters,
                          tableName: String): Future[Seq[ReindexPayload]] =
    source match {
      case ReindexSource.Calm =>
        recordReader
          .findRecords[CalmReindexPayload](reindexParameters, tableName)

      case ReindexSource.Mets =>
        recordReader
          .findRecords[MetsReindexPayload](reindexParameters, tableName)

      case ReindexSource.Miro =>
        recordReader
          .findRecords[MiroReindexPayload](reindexParameters, tableName)

      case ReindexSource.MiroInventory =>
        recordReader.findRecords[MiroInventoryReindexPayload](
          reindexParameters,
          tableName)

      case ReindexSource.Sierra =>
        recordReader
          .findRecords[SierraReindexPayload](reindexParameters, tableName)
    }
}
