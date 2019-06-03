package uk.ac.wellcome.platform.transformer.miro.services

import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.BigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.vhs.Entry

import scala.util.{Failure, Success, Try}

class MiroVHSRecordReceiver[Destination](
  objectStore: ObjectStore[MiroRecord],
  messageSender: BigMessageSender[Destination, TransformedBaseWork])
    extends Logging {

  type MiroEntry = Entry[String, MiroMetadata]

  def receiveMessage(message: NotificationMessage,
                     transformToWork: (
                       MiroRecord,
                       MiroMetadata,
                       Int) => Try[TransformedBaseWork]): Try[Unit] =
    for {
      entry <- fromJson[MiroEntry](message.body)
      miroRecord <- getTransformable(entry)
      work <- transformToWork(miroRecord, entry.metadata, entry.version)
      notification <- messageSender.sendT(work)
      _ = debug(
        s"Published work: ${work.sourceIdentifier} with message $notification")
    } yield ()

  private def getTransformable(entry: MiroEntry): Try[MiroRecord] =
    objectStore.get(entry.location) match {
      case Right(record)      => Success(record)
      case Left(storageError) => Failure(storageError.e)
    }
}
