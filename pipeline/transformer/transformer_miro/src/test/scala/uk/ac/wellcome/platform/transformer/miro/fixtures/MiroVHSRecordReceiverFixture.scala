package uk.ac.wellcome.platform.transformer.miro.fixtures

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.services.{
  BackwardsCompatObjectLocation,
  HybridRecord,
  MiroVHSRecordReceiver
}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.store.memory.MemoryStore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

trait MiroVHSRecordReceiverFixture extends MiroRecordGenerators with SQS {

  type MiroStore = Store[ObjectLocation, MiroRecord]

  private val store: MiroStore = new MemoryStore(Map.empty)

  def createRecordReceiverWith(messageSender: MemoryMessageSender): MiroVHSRecordReceiver[String] =
    new MiroVHSRecordReceiver(messageSender, store)

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
    id: String = Random.alphanumeric take 10 mkString): HybridRecord = {
    val location = ObjectLocation(namespace, id)

    store.put(location)(miroRecord)
    HybridRecord(
      id = id,
      version = version,
      location =
        BackwardsCompatObjectLocation(location.namespace, location.path)
    )
  }
}
