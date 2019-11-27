package uk.ac.wellcome.platform.transformer.mets.service

import akka.Done
import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.{BigMessageSender, EmptyMetadata}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.mets.parsers.MetsXmlParser
import uk.ac.wellcome.storage.store.{HybridStoreEntry, VersionedStore}
import uk.ac.wellcome.storage.{Identified, Version}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class MetsTransformerWorkerService(
  msgStream: SQSStream[NotificationMessage],
  messageSender: BigMessageSender[SNSConfig, TransformedBaseWork],
  store: VersionedStore[String, Int, HybridStoreEntry[String, EmptyMetadata]])
    extends Runnable
    with Logging {

  val className = this.getClass.getSimpleName

  def run(): Future[Done] =
    msgStream.foreach(this.getClass.getSimpleName, processAndLog)

  def processAndLog(message: NotificationMessage): Future[Unit] = {
    val tried = for {
      key <- fromJson[Version[String, Int]](message.body)
      _ <- process(key).toTry
    } yield ()
    Future.fromTry(tried.recover {
      case t =>
        error(s"There was an error processing $message: ", t)
        throw t
    })
  }

  private def process(key: Version[String, Int]) = {
    for {
      metsString <- fetchMets(key)
      mets <- MetsXmlParser(metsString)
      work <- mets.toWork(key.version)
      _ <- messageSender.sendT(work).toEither
    } yield ()
  }

  private def fetchMets(key: Version[String, Int]) = {
    store.get(key) match {
      case Left(err)                   => Left(err.e)
      case Right(Identified(_, entry)) => Right(entry.t)
    }
  }
}

case class VersionedMets(version: Int, metsString: String)
