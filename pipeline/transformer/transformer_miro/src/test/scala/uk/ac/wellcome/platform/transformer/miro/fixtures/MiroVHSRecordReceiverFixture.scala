package uk.ac.wellcome.platform.transformer.miro.fixtures

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.services.{
  HybridRecord,
  MiroVHSRecordReceiver
}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.store.memory.MemoryStore

import scala.concurrent.ExecutionContext.Implicits.global

trait MiroVHSRecordReceiverFixture extends MiroRecordGenerators with SQS {

  type MiroStore = Store[S3ObjectLocation, MiroRecord]

  private val store: MiroStore = new MemoryStore(initialEntries = Map.empty)

  def createRecordReceiverWith(
    messageSender: MemoryMessageSender = new MemoryMessageSender())
    : MiroVHSRecordReceiver[String] =
    new MiroVHSRecordReceiver(messageSender, store)

  def createRecordReceiver: MiroVHSRecordReceiver[String] =
    createRecordReceiverWith()

  def createHybridRecordNotificationWith(
    miroRecord: MiroRecord = createMiroRecord,
    miroMetadata: MiroMetadata = MiroMetadata(isClearedForCatalogueAPI = true),
    version: Int = 1): NotificationMessage = {

    val hybridRecord = createHybridRecordWith(miroRecord, miroMetadata, version)
    createNotificationMessageWith(
      s"""
         |{
         |  "id": "${hybridRecord.id}",
         |  "location": ${toJson(hybridRecord.location).get},
         |  "version": ${hybridRecord.version},
         |  "isClearedForCatalogueAPI": ${toJson(
           miroMetadata.isClearedForCatalogueAPI).get}
         |}
       """.stripMargin
    )
  }

  def createHybridRecordNotification: NotificationMessage =
    createHybridRecordNotificationWith()

  def createHybridRecordWith(
    miroRecord: MiroRecord = createMiroRecord,
    miroMetadata: MiroMetadata = MiroMetadata(isClearedForCatalogueAPI = true),
    version: Int = 1,
    namespace: String = "test",
    id: String = randomAlphanumeric()): HybridRecord = {
    val location = S3ObjectLocation(namespace, id)

    store.put(location)(miroRecord)

    HybridRecord(id = id, version = version, location = location)
  }
}
