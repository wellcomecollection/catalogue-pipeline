package uk.ac.wellcome.platform.transformer.sierra.services

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import grizzled.slf4j.Logging

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.work.internal.TransformedBaseWork

import uk.ac.wellcome.storage.store.{Store, HybridStoreEntry}
import uk.ac.wellcome.storage.{ObjectLocation, Version, Identified}

case class EmptyMetadata()

// In future we should just rerceive the ID and version from the adaptor as the
// S3 specific `location` field is an implementation detail we should not be
// concerned with here.
case class HybridRecord(
  id: String,
  version: Int,
  location: ObjectLocation
)

class HybridRecordReceiver[MsgDestination](
  msgSender: BigMessageSender[MsgDestination, TransformedBaseWork],
  store: Store[
    Version[String, Int],
    HybridStoreEntry[SierraTransformable, EmptyMetadata]])
    extends Logging {

  def receiveMessage(message: NotificationMessage,
                     transformToWork: (
                       SierraTransformable,
                       Int) => Try[TransformedBaseWork]): Future[Unit] = {
    debug(s"Starting to process message $message")

    Future.fromTry {
      for {
        record <- fromJson[HybridRecord](message.body)
        transformable <- getTransformable(Version(record.id, record.version))
        work <- transformToWork(transformable, record.version)
        msgNotification <- msgSender.sendT(work)
        _ = debug(
          s"Published work: ${work.sourceIdentifier} with message $msgNotification")
      } yield ()
    }
  }

  private def getTransformable(key: Version[String, Int]) : Try[SierraTransformable] =
    store.get(key) match {
      case Right(Identified(_, HybridStoreEntry(transformable, _))) => Success(transformable)
      case Left(error) => Failure(error.e)
    }
}
