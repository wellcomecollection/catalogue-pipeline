package uk.ac.wellcome.platform.recorder.services

import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import akka.Done

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.typesafe.Runnable
import uk.ac.wellcome.json.JsonUtil._

import uk.ac.wellcome.bigmessaging.EmptyMetadata
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.bigmessaging.message.BigMessageStream

import uk.ac.wellcome.storage.store.{HybridStoreEntry, VersionedStore}
import uk.ac.wellcome.storage.{Identified, Version}

class RecorderWorkerService[MsgDestination](
  store: VersionedStore[String,
                        Int,
                        HybridStoreEntry[TransformedBaseWork, EmptyMetadata]],
  messageStream: BigMessageStream[TransformedBaseWork],
  msgSender: MessageSender[MsgDestination])
    extends Runnable {

  def run(): Future[Done] =
    messageStream.foreach(this.getClass.getSimpleName, processMessage)

  private def processMessage(work: TransformedBaseWork): Future[Unit] =
    Future.fromTry {
      for {
        key <- storeWork(work)
        _ <- msgSender.sendT[Version[String, Int]](key)
      } yield ()
    }

  private def createEntry(work: TransformedBaseWork) =
    HybridStoreEntry(work, EmptyMetadata())

  private def storeWork(
    work: TransformedBaseWork): Try[Version[String, Int]] = {
    val result =
      store.upsert(work.sourceIdentifier.toString)(createEntry(work)) {
        case HybridStoreEntry(existingWork, _) =>
          createEntry(
            if (existingWork.version > work.version) { existingWork } else {
              work
            })
      }
    result match {
      case Right(Identified(key, _)) => Success(key)
      case Left(error)               => Failure(error.e)
    }
  }
}
