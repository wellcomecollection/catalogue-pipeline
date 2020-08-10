package uk.ac.wellcome.relation_embedder

import akka.Done

import scala.concurrent.Future
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream

class RelationEmbedderWorkerService[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  msgSender: MessageSender[MsgDestination]
) extends Runnable {

  def run(): Future[Done] =
    ???
}
