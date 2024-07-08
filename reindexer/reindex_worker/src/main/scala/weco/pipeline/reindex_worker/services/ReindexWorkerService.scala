package weco.pipeline.reindex_worker.services

import org.apache.pekko.Done
import org.scanamo.generic.auto._
import weco.json.JsonUtil._
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.pipeline.reindex_worker.models.source._
import weco.pipeline.reindex_worker.models.{
  ReindexJobConfig,
  ReindexParameters,
  ReindexRequest,
  ReindexSource
}
import weco.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

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
          s"No such job config: ${reindexRequest.jobConfigId}"
        )
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

  private def readRecords(
    source: ReindexSource,
    reindexParameters: ReindexParameters,
    tableName: String
  ): Future[Seq[ReindexPayload]] =
    source match {
      case ReindexSource.Calm =>
        recordReader
          .findRecords[CalmReindexPayload](reindexParameters, tableName)

      case ReindexSource.Mets =>
        recordReader
          .findRecords[MetsReindexPayload](reindexParameters, tableName)

      case ReindexSource.Tei =>
        recordReader
          .findRecords[TeiReindexPayload](reindexParameters, tableName)

      case ReindexSource.Miro =>
        recordReader
          .findRecords[MiroReindexPayload](reindexParameters, tableName)

      case ReindexSource.MiroInventory =>
        recordReader.findRecords[MiroInventoryReindexPayload](
          reindexParameters,
          tableName
        )

      case ReindexSource.Sierra =>
        recordReader
          .findRecords[SierraReindexPayload](reindexParameters, tableName)
    }
}
