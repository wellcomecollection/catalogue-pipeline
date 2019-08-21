package uk.ac.wellcome.platform.recorder.services

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import akka.Done

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.models.Implicits._

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.bigmessaging.message.MessageStream

import uk.ac.wellcome.storage.store.{VersionedStore, HybridStoreEntry}
import uk.ac.wellcome.storage.{Identified, ObjectLocation}

case class EmptyMetadata()

class RecorderWorkerService[MsgDestination](
  store: VersionedStore[
    String,
    Int,
    HybridStoreEntry[TransformedBaseWork, EmptyMetadata]],
  messageStream: MessageStream[TransformedBaseWork],
  msgSender: MessageSender[MsgDestination])
    extends Runnable {

  def run(): Future[Done] =
    messageStream.foreach(this.getClass.getSimpleName, processMessage)

  private def processMessage(work: TransformedBaseWork): Future[Unit] =
    Future.fromTry {
      for {
        location <- storeWork(work)
        _ <- msgSender.sendT(location)
      } yield ()
    }

  private def createEntry(work:  TransformedBaseWork) =
    HybridStoreEntry(work, EmptyMetadata())

  private def storeWork(
    work: TransformedBaseWork): Try[ObjectLocation] = {
    val result = store.upsert(work.sourceIdentifier.toString)(createEntry(work)) {
      case HybridStoreEntry(existingWork, _) => createEntry(
        if (existingWork.version > work.version) { existingWork }
        else { work })
    }
    result match {
      case Right(Identified(key_, _)) =>
        Success(ObjectLocation("fill/in", "later"))
      case Left(error) => Failure(error.e)
    }
  }
}
