package uk.ac.wellcome.platform.inference_manager.services

import akka.Done
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.BaseWork
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class InferenceManagerWorkerService[Destination](
  msgStream: SQSStream[NotificationMessage],
  msgSender: BigMessageSender[Destination, BaseWork]
)(implicit ec: ExecutionContext)
    extends Runnable {
  def run(): Future[Done] = ???
}
