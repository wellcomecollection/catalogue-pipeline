package uk.ac.wellcome.platform.matcher.services

import akka.Done
import akka.actor.ActorSystem
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.message.MessageStream
import uk.ac.wellcome.messaging.sns.SNSWriter
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.models.VersionExpectedConflictException
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class MatcherWorkerService[Destination](
  messageStream: MessageStream[TransformedBaseWork],
  messageSender: MessageSender[Destination],
  workMatcher: WorkMatcher)(
  implicit val actorSystem: ActorSystem,
  ec: ExecutionContext)
    extends Logging
    with Runnable {

  def run(): Future[Done] =
    messageStream.foreach(this.getClass.getSimpleName, processMessage)

  def processMessage(work: TransformedBaseWork): Future[Unit] = {
    (for {
      identifiersList <- workMatcher.matchWork(work)
      _ <- Future.fromTry {
        messageSender.sendT(identifiersList)
      }
    } yield ()).recover {
      case e: VersionExpectedConflictException =>
        debug(
          s"Not matching work due to version conflict exception: ${e.getMessage}")
    }
  }
}
