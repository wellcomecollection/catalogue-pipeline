package weco.catalogue.sierra_holdings_merger.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class SierraHoldingsMergerWorkerService[Destination](
  sqsStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Destination]
) extends Runnable {

  def process(message: NotificationMessage): Future[Unit] = {
    println(s"I got a message! $message")
    Future.successful(())
  }

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, process)
}
