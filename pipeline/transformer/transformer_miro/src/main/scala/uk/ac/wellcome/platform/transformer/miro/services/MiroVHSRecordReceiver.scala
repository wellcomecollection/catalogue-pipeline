package uk.ac.wellcome.platform.transformer.miro.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import grizzled.slf4j.Logging

import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.miro.exceptions.MiroTransformerException
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.json.JsonUtil._

import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage

import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.{Identified, ObjectLocation}

// In future we should just receive the ID and version from the adapter as the
// S3 specific `location` field is an implementation detail we should not be
// concerned with here.
case class HybridRecord(
  id: String,
  version: Int,
  location: BackwardsCompatObjectLocation
)

case class BackwardsCompatObjectLocation(namespace: String, key: String)

class MiroVHSRecordReceiver[MsgDestination](
  msgSender: BigMessageSender[MsgDestination, TransformedBaseWork],
  store: Store[ObjectLocation, MiroRecord])(
  implicit ec: ExecutionContext)
    extends Logging {

  def receiveMessage(message: NotificationMessage,
                     transformToWork: (
                       MiroRecord,
                       MiroMetadata,
                       Int) => Try[TransformedBaseWork]): Future[Unit] = {
    debug(s"Starting to process message $message")

    val msgNotification = Future.fromTry {
      for {
        hybridRecord <- fromJson[HybridRecord](message.body)
        metadata <- fromJson[MiroMetadata](message.body)
        record <- getRecord(hybridRecord)
        work <- transformToWork(record, metadata, hybridRecord.version)
        msgNotification <- msgSender.sendT(work)
        _ = debug(
          s"Published work: ${work.sourceIdentifier} with message $msgNotification")
      } yield msgNotification
    }

    msgNotification
      .recover {
        case e: JsonDecodingError =>
          info(
            "Recoverable failure parsing HybridRecord/MiroMetadata from JSON",
            e)
          throw MiroTransformerException(e)
      }
      .map(_ => ())
  }

  private def getRecord(record: HybridRecord): Try[MiroRecord] =
    record match {
      case HybridRecord(_, _, BackwardsCompatObjectLocation(namespace, path)) =>
        store.get(ObjectLocation(namespace, path)) match {
          case Right(Identified(_, miroRecord)) =>
            Success(miroRecord)
          case Left(error) => Failure(error.e)
        }
    }
}
