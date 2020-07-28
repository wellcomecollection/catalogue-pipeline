package uk.ac.wellcome.platform.transformer.mets.service

import akka.Done
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.mets_adapter.models.MetsLocation
import uk.ac.wellcome.platform.transformer.mets.transformer.MetsXmlTransformer
import uk.ac.wellcome.storage.store.{Readable, VersionedStore}
import uk.ac.wellcome.storage.{Identified, ObjectLocation, Version}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future

class MetsTransformerWorkerService[Destination](
  msgStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Destination],
  adapterStore: VersionedStore[String, Int, MetsLocation],
  metsXmlStore: Readable[ObjectLocation, String]
) extends Runnable
    with Logging {

  type Result[T] = Either[Throwable, T]

  val className = this.getClass.getSimpleName

  val xmlTransformer = new MetsXmlTransformer(metsXmlStore)

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

  private def process(key: Version[String, Int]): Either[Throwable, Unit] =
    for {
      metsLocation <- getMetsLocation(key)
      metsData <- xmlTransformer.transform(metsLocation)
      work <- metsData.toWork(key.version)
      _ <- messageSender.sendT(work).toEither
    } yield ()

  private def getMetsLocation(key: Version[String, Int]): Result[MetsLocation] =
    adapterStore.get(key) match {
      case Left(err)                   => Left(err.e)
      case Right(Identified(_, entry)) => Right(entry)
    }
}
