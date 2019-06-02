package uk.ac.wellcome.platform.transformer.sierra.services

import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.{BigMessageSender, MessageSender}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry}

import scala.util.Try

class RecordReceiver[Destination](bigMessageSender: MessageSender[Destination],
                                  objectStore: ObjectStore[SierraTransformable])
    extends Logging {

  def receiveMessage(message: NotificationMessage,
                     transformToWork: (
                       SierraTransformable,
                       Int) => Try[TransformedBaseWork]): Try[Unit] = {
    debug(s"Starting to process message $message")

    for {
      entry <- fromJson[Entry[String, EmptyMetadata]](message.body)
      transformable <- objectStore.get(entry.location)
      work <- transformToWork(transformable, entry.version)
      publishResult <- bigMessageSender.sendT(work)
      _ = debug(
        s"Published work: ${work.sourceIdentifier} with message $publishResult")
    } yield ()
  }
}
