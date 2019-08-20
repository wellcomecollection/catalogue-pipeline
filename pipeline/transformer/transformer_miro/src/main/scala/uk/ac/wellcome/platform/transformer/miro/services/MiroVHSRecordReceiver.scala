package uk.ac.wellcome.platform.transformer.miro.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import grizzled.slf4j.Logging

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.miro.exceptions.MiroTransformerException
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord

import uk.ac.wellcome.bigmessaging.BigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage

import uk.ac.wellcome.storage.store.{HybridStoreEntry, Store}
import uk.ac.wellcome.storage.{Identified, ObjectLocation, Version}

// In future we should just receive the ID and version from the adaptor as the
// S3 specific `location` field is an implementation detail we should not be
// concerned with here.
case class HybridRecord(
  id: String,
  version: Int,
  location: ObjectLocation
)

class MiroVHSRecordReceiver[MsgDestination](
  msgSender: BigMessageSender[MsgDestination, TransformedBaseWork],
  store: Store[Version[String, Int],
               HybridStoreEntry[MiroRecord, MiroMetadata]])(
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
        record <- fromJson[HybridRecord](message.body)
        (miroRecord, miroMetadata) <- getRecordAndMetadata(
          Version(record.id, record.version))
        work <- transformToWork(miroRecord, miroMetadata, record.version)
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

  private def getRecordAndMetadata(
    key: Version[String, Int]): Try[(MiroRecord, MiroMetadata)] =
    store.get(key) match {
      case Right(Identified(_, HybridStoreEntry(miroRecord, miroMetadata))) =>
        Success((miroRecord, miroMetadata))
      case Left(error) => Failure(error.e)
    }
}
