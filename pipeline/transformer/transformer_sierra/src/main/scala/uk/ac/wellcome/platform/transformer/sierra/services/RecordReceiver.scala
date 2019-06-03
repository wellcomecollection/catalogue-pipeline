package uk.ac.wellcome.platform.transformer.sierra.services

import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.BigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry}

import scala.util.{Failure, Success, Try}

class RecordReceiver[Destination](
  objectStore: ObjectStore[SierraTransformable],
  messageSender: BigMessageSender[Destination, TransformedBaseWork])
    extends Logging {

  def receiveMessage(message: NotificationMessage,
                     transformToWork: (
                       SierraTransformable,
                       Int) => Try[TransformedBaseWork]): Try[Unit] = {
    debug(s"Starting to process message $message")

    for {
      entry <- fromJson[Entry[String, EmptyMetadata]](message.body)
      transformable <- getTransformable(entry)
      work <- transformToWork(transformable, entry.version)
      publishResult <- messageSender.sendT(work)
      _ = debug(
        s"Published work: ${work.sourceIdentifier} with message $publishResult")
    } yield ()
  }

  private def getTransformable(entry: Entry[String, EmptyMetadata]): Try[SierraTransformable] =
    objectStore.get(entry.location) match {
      case Right(record)      => Success(record)
      case Left(storageError) => Failure(storageError.e)
    }
}
