package uk.ac.wellcome.platform.ingestor.common

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream._
import uk.ac.wellcome.pipeline_storage.{Indexable, Indexer, PipelineStorageConfig, Retriever}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class IngestorWorkerService[Destination, In, Out](
                                                   msgStream: SQSStream[NotificationMessage],
                                                   indexer: Indexer[Out],
                                                   config: PipelineStorageConfig,
                                                   messageSender: MessageSender[Destination],
  workRetriever: Retriever[In],
  transform: In => Out)(implicit ec: ExecutionContext,
                        indexable: Indexable[Out])
    extends Runnable {

  def run(): Future[Done] =
    for {
      _ <- indexer.init()
      _ <- msgStream.runStream(
        this.getClass.getSimpleName,
        source =>
          source
            .via(batchRetrieveFlow(config, workRetriever))
            .via(processFlow(config, bundle => processMessage(bundle.item)))
            .via(
              broadcastAndMerge(
                batchIndexAndSendFlow(
                  config,
                  (doc: Out) =>
                    sendIndexable(messageSender)(doc),
                  indexer),
                noOutputFlow))
      )
    } yield Done

  private def processMessage(item: In): Future[List[Out]] =
    Future.successful(List(transform(item)))

}
