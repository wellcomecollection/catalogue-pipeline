package uk.ac.wellcome.platform.transformer.calm.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.platform.transformer.calm.CalmTransformableTransformer
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class CalmTransformerWorkerService[MsgDestination, MsgIn](
  messageReceiver: HybridRecordReceiver[MsgDestination, MsgIn],
  sqsStream: SQSStream[NotificationMessage]
) extends Runnable {

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)

  private def processMessage(message: NotificationMessage): Future[Unit] =
    messageReceiver.receiveMessage(message, CalmTransformableTransformer.apply)
}
