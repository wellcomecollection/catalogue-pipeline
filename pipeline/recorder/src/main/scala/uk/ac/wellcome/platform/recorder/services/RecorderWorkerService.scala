package uk.ac.wellcome.platform.recorder.services

import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import akka.Done
import io.circe.{Encoder, Json}

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.typesafe.Runnable

import uk.ac.wellcome.bigmessaging.typesafe.{EmptyMetadata, GetLocation}
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.bigmessaging.message.BigMessageStream

import uk.ac.wellcome.storage.store.{HybridStoreEntry, VersionedStore}
import uk.ac.wellcome.storage.{Identified, Version}

// The ObjectLocation format used in the matcher is from the old storage lib and
// differs to the ObjectLocation used in the storage lib here. For that reason
// we manually encode a "RemoteNotification" type with the expected fields
case class MatcherNotification(namespace: String, key: String)

class RecorderWorkerService[MsgDestination](
  store: VersionedStore[
    String,
    Int,
    HybridStoreEntry[TransformedBaseWork, EmptyMetadata]] with GetLocation,
  messageStream: BigMessageStream[TransformedBaseWork],
  msgSender: MessageSender[MsgDestination])
    extends Runnable {

  implicit val encodeMatcherNotification = new Encoder[MatcherNotification] {
    final def apply(value: MatcherNotification): Json =
      Json.obj(
        ("type", Json.fromString("RemoteNotification")),
        ("location",
          Json.obj(
            ("namespace", Json.fromString(value.namespace)),
            ("key", Json.fromString(value.key))
          )
        )
      )
  }

  def run(): Future[Done] =
    messageStream.foreach(this.getClass.getSimpleName, processMessage)

  private def processMessage(work: TransformedBaseWork): Future[Unit] =
    Future.fromTry {
      for {
        key <- storeWork(work)
        location <- store.getLocation(key)
        _ <- msgSender.sendT(
          MatcherNotification(location.namespace, location.path)
        )
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
