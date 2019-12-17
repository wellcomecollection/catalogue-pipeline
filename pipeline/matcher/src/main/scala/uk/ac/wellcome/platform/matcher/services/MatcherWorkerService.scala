package uk.ac.wellcome.platform.matcher.services

import akka.Done
import akka.actor.ActorSystem
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.models.VersionExpectedConflictException
import uk.ac.wellcome.platform.matcher.storage.WorkStore
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class MatcherWorkerService[MsgDestination](
                                            store: WorkStore,
                                            msgStream: SQSStream[NotificationMessage],
                                            msgSender: MessageSender[MsgDestination],
                                            workMatcher: WorkMatcher)(implicit val actorSystem: ActorSystem,
                            ec: ExecutionContext)
    extends Logging
    with Runnable {

  def run(): Future[Done] =
    msgStream.foreach(this.getClass.getSimpleName, processMessage)

  def processMessage(message: NotificationMessage): Future[Unit] = {
    (for {
      key <- Future.fromTry(fromJson[Version[String, Int]](message.body))
      work <- store.getWork(key)
      identifiersList <- workMatcher.matchWork(work)
      _ <- Future.fromTry(msgSender.sendT(identifiersList))
    } yield ()).recover {
      case MatcherException(e: VersionExpectedConflictException) =>
        debug(
          s"Not matching work due to version conflict exception: ${e.getMessage}")
    }
  }
}
