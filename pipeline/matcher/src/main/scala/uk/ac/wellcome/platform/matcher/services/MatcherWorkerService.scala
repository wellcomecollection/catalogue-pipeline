package uk.ac.wellcome.platform.matcher.services

import akka.Done
import akka.actor.ActorSystem
import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.EmptyMetadata
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sqs.NotificationStream
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.matcher.exceptions.MatcherException
import uk.ac.wellcome.platform.matcher.matcher.WorkMatcher
import uk.ac.wellcome.platform.matcher.models.VersionExpectedConflictException
import uk.ac.wellcome.storage.store.{HybridStoreEntry, VersionedStore}
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class MatcherWorkerService[MsgDestination](
  store: VersionedStore[String,
                        Int,
                        HybridStoreEntry[TransformedBaseWork, EmptyMetadata]],
  msgStream: NotificationStream[Version[String, Int]],
  msgSender: MessageSender[MsgDestination],
  workMatcher: WorkMatcher)(implicit val actorSystem: ActorSystem,
                            ec: ExecutionContext)
    extends Logging
    with Runnable {

  def run(): Future[Done] = msgStream.run(processMessage)

  def processMessage(key: Version[String, Int]): Future[Unit] = {
    (for {
      work <- getWork(key)
      identifiersList <- workMatcher.matchWork(work)
      _ <- Future.fromTry(msgSender.sendT(identifiersList))
    } yield ()).recover {
      case MatcherException(e: VersionExpectedConflictException) =>
        debug(
          s"Not matching work due to version conflict exception: ${e.getMessage}")
    }
  }

  def getWork(key: Version[String, Int]): Future[TransformedBaseWork] =
    store.get(key) match {
      case Left(err) =>
        error(s"Error fetching $key from VHS")
        Future.failed(err.e)
      case Right(Identified(_, entry)) => Future.successful(entry.t)
    }
}
