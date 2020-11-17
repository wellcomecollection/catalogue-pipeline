package uk.ac.wellcome.platform.router

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Merged}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{Indexer, Retriever}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class RouterWorkerService[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  worksMsgSender: MessageSender[MsgDestination],
  pathsMsgSender: MessageSender[MsgDestination],
  workRetriever: Retriever[Work[Merged]],
  workIndexer: Indexer[Work[Denormalised]]
)(implicit ec: ExecutionContext)
    extends Runnable {

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)

  private def processMessage(message: NotificationMessage): Future[Unit] = {
    workRetriever.apply(message.body).flatMap { work =>
      work.data.collectionPath
        .fold(ifEmpty = {
          for {
            worksEither <- workIndexer.index(
              work.transition[Denormalised](Relations.none))
            _ <- worksEither match {
              case Left(failedWorks) =>
                Future.failed(new Exception(s"Failed indexing $failedWorks"))
              case Right(_) => Future.successful(())
            }
            _ <- Future.fromTry(worksMsgSender.send(work.id))
          } yield ()
        }) { path =>
          Future.fromTry(pathsMsgSender.send(path.path))
        }
    }
  }
}
