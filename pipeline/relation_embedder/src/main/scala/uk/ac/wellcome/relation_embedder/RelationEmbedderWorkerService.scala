package uk.ac.wellcome.relation_embedder

import akka.Done

import scala.concurrent.{ExecutionContext, Future}

import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.pipeline_storage.Retriever
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.json.JsonUtil._

class RelationEmbedderWorkerService[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination],
  workRetriever: Retriever[IdentifiedBaseWork],
  relatedWorksService: RelatedWorksService,
)(implicit ec: ExecutionContext) extends Runnable {

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)

  def processMessage(message: NotificationMessage): Future[Unit] =
    for {
      work <- workRetriever(message.body)
    } yield ()
}
