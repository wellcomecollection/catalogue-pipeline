package uk.ac.wellcome.platform.transformer.miro.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.platform.transformer.miro.MiroRecordTransformer
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class MiroTransformerWorkerService[Destination](
  vhsRecordReceiver: MiroVHSRecordReceiver[Destination],
  miroTransformer: MiroRecordTransformer,
  sqsStream: SQSStream[NotificationMessage]
) extends Runnable {

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)

  private def processMessage(message: NotificationMessage): Future[Unit] =
    Future.fromTry {
      vhsRecordReceiver.receiveMessage(message, miroTransformer.transform)
    }
}
