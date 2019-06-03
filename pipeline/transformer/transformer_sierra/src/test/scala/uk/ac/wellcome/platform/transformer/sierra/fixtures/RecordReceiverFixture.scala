package uk.ac.wellcome.platform.transformer.sierra.fixtures

import org.scalatest.EitherValues
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.memory.MemoryBigMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.SierraTransformable._
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.sierra.services.RecordReceiver
import uk.ac.wellcome.storage.memory.MemoryObjectStore
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry}

import scala.util.Random

trait RecordReceiverFixture
    extends SierraGenerators
    with EitherValues
    with SQS {
  type SierraTransformableStore = MemoryObjectStore[SierraTransformable]
  type WorkSender = MemoryBigMessageSender[TransformedBaseWork]

  def createRecordReceiver(
    store: SierraTransformableStore = new SierraTransformableStore(),
    sender: WorkSender = new WorkSender()
  ): RecordReceiver[String] =
    new RecordReceiver(store, sender)

  def createSierraNotificationMessageWith(
    store: SierraTransformableStore,
    sierraTransformable: SierraTransformable = createSierraTransformable,
    version: Int = 1
  ): NotificationMessage = {
    val location = store
      .put(
        namespace = Random.alphanumeric.take(8) mkString
      )(
        input = sierraTransformable
      )
      .right
      .value

    val entry = Entry(
      id = Random.alphanumeric.take(8) mkString,
      version = version,
      location = location,
      metadata = EmptyMetadata()
    )

    createNotificationMessageWith(entry)
  }
}
