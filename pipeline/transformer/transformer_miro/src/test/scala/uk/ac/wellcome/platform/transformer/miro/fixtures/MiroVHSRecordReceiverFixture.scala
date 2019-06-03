package uk.ac.wellcome.platform.transformer.miro.fixtures

import org.scalatest.EitherValues
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.services.MiroVHSRecordReceiver
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.memory.MemoryObjectStore
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.vhs.Entry

import scala.util.Random

trait MiroVHSRecordReceiverFixture extends SQS with MiroRecordGenerators with EitherValues {
  type MiroRecordStore = MemoryObjectStore[MiroRecord]
  type WorkSender = MemoryBigMessageSender[TransformedBaseWork]

  def createRecordReceiver(
    store: MiroRecordStore = new MiroRecordStore(),
    sender: WorkSender = new WorkSender()
  ): MiroVHSRecordReceiver[String] =
    new MiroVHSRecordReceiver(store, sender)

  def createMiroVHSRecordNotificationMessageWith(
    store: MiroRecordStore,
    miroRecord: MiroRecord = createMiroRecord,
    version: Int = 1
  ): NotificationMessage = {
    val location = store.put(
      namespace = Random.alphanumeric.take(8) mkString
    )(
      input = miroRecord
    ).right.value

    val entry = Entry(
      id = Random.alphanumeric.take(8) mkString,
      version = version,
      location = location,
      metadata = MiroMetadata(isClearedForCatalogueAPI = true)
    )

    createNotificationMessageWith(entry)
  }
}
