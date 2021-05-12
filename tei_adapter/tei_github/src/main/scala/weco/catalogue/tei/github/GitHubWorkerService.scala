package weco.catalogue.tei.github

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

case class GitHubWorkerService[Destination](messageStream: SQSStream[NotificationMessage], messageSender: MessageSender[Destination], gitHubRepoUrl: String) extends Runnable{
  override def run(): Future[Any] = ???
}
