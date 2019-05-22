package uk.ac.wellcome.platform.reindex.reindex_worker.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.platform.reindex.reindex_worker.models.{
  ReindexJobConfig,
  ReindexRequest
}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class ReindexWorkerService[DestinationConfig](
  recordReader: RecordReader,
  bulkMessageSender: BulkMessageSender[DestinationConfig],
  sqsStream: SQSStream[NotificationMessage],
  reindexJobConfigMap: Map[String, ReindexJobConfig[DestinationConfig]]
)(implicit ec: ExecutionContext)
    extends Runnable {

  private def processMessage(message: NotificationMessage): Future[Unit] =
    for {
      reindexRequest: ReindexRequest <- Future.fromTry(
        fromJson[ReindexRequest](message.body))

      // @@AWLC: This throw isn't wrapped in a Future or a Try -- will it be
      // handled correctly, or would it crash the app?
      reindexJobConfig = reindexJobConfigMap.getOrElse(
        reindexRequest.jobConfigId,
        throw new RuntimeException(
          s"No such job config: ${reindexRequest.jobConfigId}")
      )

      recordsToSend <- Future.fromTry {
        recordReader
          .findRecordsForReindexing(
            reindexParameters = reindexRequest.parameters,
            dynamoConfig = reindexJobConfig.dynamoConfig
          )
      }
      _ <- bulkMessageSender.send(
        recordsToSend,
        destination = reindexJobConfig.destination
      )
    } yield ()

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)
}
