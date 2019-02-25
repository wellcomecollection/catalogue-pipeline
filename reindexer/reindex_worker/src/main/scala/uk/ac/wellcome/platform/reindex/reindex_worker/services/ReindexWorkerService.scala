package uk.ac.wellcome.platform.reindex.reindex_worker.services

import akka.Done
import uk.ac.wellcome.messaging.sqs.NotificationStream
import uk.ac.wellcome.platform.reindex.reindex_worker.models.{
  ReindexJobConfig,
  ReindexRequest
}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class ReindexWorkerService(
  notificationStream: NotificationStream[ReindexRequest],
  recordReader: RecordReader,
  bulkSNSSender: BulkSNSSender,
  reindexJobConfigMap: Map[String, ReindexJobConfig]
)(implicit ec: ExecutionContext)
    extends Runnable {

  def run(): Future[Done] =
    notificationStream.run(processMessage)

  def processMessage(reindexRequest: ReindexRequest): Future[Unit] =
    for {
      reindexJobConfig <- Future.successful(
        reindexJobConfigMap.getOrElse(
          reindexRequest.jobConfigId,
          throw new RuntimeException(
            s"No such job config: ${reindexRequest.jobConfigId}")
        ))
      recordsToSend: List[String] <- recordReader
        .findRecordsForReindexing(
          reindexParameters = reindexRequest.parameters,
          dynamoConfig = reindexJobConfig.dynamoConfig
        )
      _ <- bulkSNSSender.sendToSNS(
        messages = recordsToSend,
        snsConfig = reindexJobConfig.snsConfig
      )
    } yield ()
}
