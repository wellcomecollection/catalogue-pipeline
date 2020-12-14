package uk.ac.wellcome.platform.router

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Merged}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class RouterWorkerService[MsgDestination](
                                           pipelineStream: PipelineStorageStream[NotificationMessage,
                                        Work[Denormalised],
                                        MsgDestination],
                                           pathsMsgSender: MessageSender[MsgDestination],
                                           workRetriever: Retriever[Work[Merged]],
)(implicit ec: ExecutionContext)
    extends Runnable {

  def run(): Future[Done] =
    pipelineStream.foreach(this.getClass.getSimpleName, processMessage)

  private def processMessage(
    message: NotificationMessage): Future[Option[Work[Denormalised]]] = {
    workRetriever.apply(message.body).flatMap { work =>
      work.data.collectionPath
        .fold[Future[Option[Work[Denormalised]]]](ifEmpty = {
          Future.successful(Some(work.transition[Denormalised](Relations.none)))
        }) { path =>
          Future.fromTry(pathsMsgSender.send(path.path)).map(_ => None)
        }
    }
  }
}
