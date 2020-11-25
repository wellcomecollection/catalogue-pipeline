package uk.ac.wellcome.platform.transformer.miro.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import grizzled.slf4j.Logging

import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.miro.exceptions.MiroTransformerException
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.Identified
import WorkState.Source

// In future we should just receive the ID and version from the adapter as the
// S3 specific `location` field is an implementation detail we should not be
// concerned with here.
case class HybridRecord(
  id: String,
  version: Int,
  location: S3ObjectLocation
)

class MiroVHSRecordReceiver[MsgDestination](
  messageSender: MessageSender[MsgDestination],
  store: Store[S3ObjectLocation, MiroRecord])(implicit ec: ExecutionContext)
    extends Logging {

  def receiveMessage(message: NotificationMessage,
                     transformToWork: (
                       MiroRecord,
                       MiroMetadata,
                       Int) => Try[Work[Source]]): Future[Unit] = {
    debug(s"Starting to process message $message")

    val msgNotification = Future.fromTry {
      for {
        hybridRecord <- fromJson[HybridRecord](message.body)
        metadata <- fromJson[MiroMetadata](message.body)
        record <- getRecord(hybridRecord)
        work <- transformToWork(record, metadata, hybridRecord.version)
        msgNotification <- messageSender.sendT(work)
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
    store.get(record.location) match {
      case Right(Identified(_, miroRecord)) => Success(miroRecord)
      case Left(error)                      => Failure(error.e)
    }
}
