package uk.ac.wellcome.platform.transformer.sierra.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.platform.transformer.sierra.SierraTransformableTransformer
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class SierraTransformerWorkerService[Destination](
  messageReceiver: RecordReceiver[Destination],
  sierraTransformer: SierraTransformableTransformer,
  sqsStream: SQSStream[NotificationMessage]
) extends Runnable {

  def run(): Future[Done] =
    sqsStream.foreach(
      this.getClass.getSimpleName,
      message =>
        Future.fromTry {
          messageReceiver.receiveMessage(message, sierraTransformer.transform)
      }
    )
}
