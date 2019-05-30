package uk.ac.wellcome.platform.transformer.miro.services

import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.messaging.message.{MessageNotification, MessageWriter}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.miro.exceptions.MiroTransformerException
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.vhs.Entry

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MiroVHSRecordReceiver(objectStore: ObjectStore[MiroRecord],
                            messageWriter: MessageWriter[TransformedBaseWork])(
  implicit ec: ExecutionContext)
    extends Logging {

  type MiroEntry = Entry[String, MiroMetadata]

  def receiveMessage(
    message: NotificationMessage,
    transformToWork: (MiroRecord, MiroMetadata, Int) => Try[TransformedBaseWork]): Future[Unit] = {
    debug(s"Starting to process message $message")

    val futureNotification = for {
      entry <- Future.fromTry {
        fromJson[MiroEntry](message.body)
      }
      miroRecord <- getTransformable(entry)
      work <- Future.fromTry(
        transformToWork(miroRecord, entry.metadata, entry.version)
      )
      notification <- publishMessage(work)
      _ = debug(
        s"Published work: ${work.sourceIdentifier} with message $notification")
    } yield notification

    futureNotification
      .recover {
        case e: JsonDecodingError =>
          info(
            "Recoverable failure parsing Entry/MiroMetadata from JSON",
            e)
          throw MiroTransformerException(e)
      }
      .map(_ => ())

  }

  private def getTransformable(entry: MiroEntry): Future[MiroRecord] =
    objectStore.get(entry.location) match {
      case Right(record) => Future.successful(record)
      case Left(storageError) => Future.failed(storageError.e)
    }

  private def publishMessage(work: TransformedBaseWork): Future[MessageNotification] =
    messageWriter.write(work)
}
