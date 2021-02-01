package uk.ac.wellcome.platform.calm_deletion_checker

import akka.Done
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.platform.calm_api_client.CalmRetriever
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class DeletionCheckerWorkerService[Destination](
  msgStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Destination],
  calmRetriever: CalmRetriever)
    extends Runnable {

  def run(): Future[Done] = Future.successful(Done)

}
