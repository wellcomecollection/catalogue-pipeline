package uk.ac.wellcome.platform.recorder.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.message.{MessageNotification, MessageStream, RemoteNotification}
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, VersionedHybridStore}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class RecorderWorkerService[Destination](
  versionedHybridStore: VersionedHybridStore[String,
                                             TransformedBaseWork,
                                             EmptyMetadata],
  messageStream: MessageStream[TransformedBaseWork],
  messageSender: MessageSender[Destination]) extends Runnable {

  def run(): Future[Done] =
    messageStream.foreach(
      this.getClass.getSimpleName,
      message => Future.fromTry { processMessage(message) }
    )

  private def processMessage(work: TransformedBaseWork): Try[Unit] =
    for {
      entry <- storeInVhs(work)
      _ <- messageSender.sendT[MessageNotification](
        RemoteNotification(entry.location)
      )
    } yield ()

  private def storeInVhs(
    work: TransformedBaseWork): Try[versionedHybridStore.VHSEntry] =
    versionedHybridStore.update(work.sourceIdentifier.toString)(
      (work, EmptyMetadata()))(
      (existingWork, existingMetadata) =>
        if (existingWork.version > work.version) {
          (existingWork, existingMetadata)
        } else {
          (work, EmptyMetadata())
      }
    ) match {
      case Right(value) => Success(value)
      case Left(storageError) => Failure(storageError.e)
    }
}
