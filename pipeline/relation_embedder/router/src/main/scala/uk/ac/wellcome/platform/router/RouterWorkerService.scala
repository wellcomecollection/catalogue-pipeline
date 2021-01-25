package uk.ac.wellcome.platform.router

import akka.stream.scaladsl.Source
import akka.{Done, NotUsed}
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Merged}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream._
import uk.ac.wellcome.pipeline_storage.{Indexable, Indexer, PipelineStorageConfig, Retriever}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class RouterWorkerService[MsgDestination](
                                           msgStream: SQSStream[NotificationMessage],
                                           indexer: Indexer[Work[Denormalised]],
                                           config: PipelineStorageConfig,
                                           messageSender: MessageSender[MsgDestination],
  pathsMsgSender: MessageSender[MsgDestination],
  workRetriever: Retriever[Work[Merged]],
)(implicit ec: ExecutionContext, indexable: Indexable[Work[Denormalised]])
    extends Runnable {
  def run(): Future[Done] =
    for {
      _ <- indexer.init()
      _ <- msgStream.runStream(
        this.getClass.getSimpleName,
        (source: Source[(Message, NotificationMessage), NotUsed]) =>
          source
            .via(batchRetrieveFlow(config, workRetriever))
            .via(processFlow(config, bundle => processMessage(bundle.item)))
            .via(
              broadcastAndMerge(batchIndexAndSendFlow(config, (doc: Work[Denormalised]) => sendIndexable(messageSender)(doc),indexer),  noOutputFlow))
      )
    } yield Done

  private def processMessage(
    work: Work[Merged]): Future[List[Work[Denormalised]]] = {
      work.data.collectionPath
        .fold[Future[List[Work[Denormalised]]]](ifEmpty = {
          Future.successful(List(work.transition[Denormalised](Relations.none)))
        }) { path =>
          Future.fromTry(pathsMsgSender.send(path.path)).map(_ => Nil)
        }
    }

}
