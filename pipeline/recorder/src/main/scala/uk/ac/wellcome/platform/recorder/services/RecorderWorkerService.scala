package uk.ac.wellcome.platform.recorder.services

import akka.Done
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.bigmessaging.message.{
  MessageNotification,
  MessageStream,
  RemoteNotification
}
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.recorder.EmptyMetadata
import uk.ac.wellcome.storage.store.{HybridStore, HybridStoreEntry}
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class RecorderWorkerService[Destination](
  vhs: HybridStore[ObjectLocation, String, TransformedBaseWork, EmptyMetadata],
  messageStream: MessageStream[TransformedBaseWork],
  messageSender: MessageSender[Destination])
    extends Runnable {

  def run(): Future[Done] =
    messageStream.foreach(
      this.getClass.getSimpleName,
      message => Future.fromTry { processMessage(message) }
    )

  private def processMessage(work: TransformedBaseWork): Try[Unit] =
    for {
      entry <- storeInVhs(work)
      _ <- messageSender.sendT[MessageNotification](
        RemoteNotification(entry)
      )
    } yield ()

  private def storeInVhs(work: TransformedBaseWork): Try[ObjectLocation] = {
    val putResult =
      vhs.put(ObjectLocation("namespace", work.sourceIdentifier.toString))(
        HybridStoreEntry(work, EmptyMetadata))

    putResult match {
      case Right(_) =>
        Success(ObjectLocation("namespace", work.sourceIdentifier.toString))
      case Left(storageError) => Failure(storageError.e)
    }
  }
}
