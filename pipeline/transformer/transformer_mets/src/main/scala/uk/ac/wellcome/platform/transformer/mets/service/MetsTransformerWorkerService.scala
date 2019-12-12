package uk.ac.wellcome.platform.transformer.mets.service

import akka.Done
import grizzled.slf4j.Logging
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.mets_adapter.models.MetsData
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.mets.parsers.MetsXmlParser
import uk.ac.wellcome.platform.transformer.mets.store.TemporaryCredentialsStore
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, ObjectLocation, Version}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class MetsTransformerWorkerService(
  msgStream: SQSStream[NotificationMessage],
  messageSender: BigMessageSender[SNSConfig, TransformedBaseWork],
  adapterStore: VersionedStore[String, Int, MetsData],
  metsXmlStore: TemporaryCredentialsStore[String]
) extends Runnable
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
      metsData <- getMetsData(key)
      metsString <- getFromMetsStore(metsData)
      mets <- MetsXmlParser(metsString)
      work <- mets.toWork(key.version)
      _ <- messageSender.sendT(work).toEither
    } yield ()
  }

  private def getMetsData(key: Version[String, Int]) = {
    adapterStore.get(key) match {
      case Left(err)                   => Left(err.e)
      case Right(Identified(_, entry)) => Right(entry)
    }
  }

  private def getFromMetsStore(metsData: MetsData) = {
    metsXmlStore.get(
      ObjectLocation(metsData.bucket, s"${metsData.path}/${metsData.file}"))
  }
}
