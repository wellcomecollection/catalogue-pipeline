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

class ReindexWorkerService[Destination](
  recordReader: RecordReader,
  bulkMessageSender: BulkMessageSender[Destination],
  sqsStream: SQSStream[NotificationMessage],
  reindexJobConfigMap: Map[String, ReindexJobConfig[Destination]]
)(implicit ec: ExecutionContext)
    extends Runnable {

  private def processMessage(message: NotificationMessage): Future[Unit] =
    for {
      reindexRequest: ReindexRequest <- Future.fromTry(
        fromJson[ReindexRequest](message.body))
      reindexJobConfig = reindexJobConfigMap.getOrElse(
        reindexRequest.jobConfigId,
        throw new RuntimeException(
          s"No such job config: ${reindexRequest.jobConfigId}")
      )
      recordsToSend <- recordReader
        .findRecordsForReindexing(
          reindexParameters = reindexRequest.parameters,
          dynamoConfig = reindexJobConfig.dynamoConfig
        )
      _ <- bulkMessageSender.send(
        messages = recordsToSend,
        destination = reindexJobConfig.destinationConfig
      )
    } yield ()

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)
}
