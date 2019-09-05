package uk.ac.wellcome.platform.transformer.miro.fixtures

import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.services.{
  HybridRecord,
  MiroVHSRecordReceiver
}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._

import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture

import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}

import uk.ac.wellcome.storage.{ObjectLocation, Version}
import uk.ac.wellcome.storage.store.{HybridStoreEntry, Store}
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.memory.MemoryStore
import uk.ac.wellcome.storage.streaming.Codec._

trait MiroVHSRecordReceiverFixture
    extends BigMessagingFixture
    with MiroRecordGenerators {

  type MiroStore = Store[
    Version[String, Int],
    HybridStoreEntry[MiroRecord, MiroMetadata]
  ]

  private val store: MiroStore = new MemoryStore(Map.empty)

  def withMiroVHSRecordReceiver[R](topic: Topic, bucket: Bucket)(
    testWith: TestWith[MiroVHSRecordReceiver[SNSConfig], R]): R = {
    withSqsBigMessageSender[TransformedBaseWork, R](bucket, topic, snsClient) {
      msgSender =>
        val recordReceiver = new MiroVHSRecordReceiver(msgSender, store)
        testWith(recordReceiver)
    }
  }

  def createHybridRecordNotificationWith(
    miroRecord: MiroRecord = createMiroRecord,
    miroMetadata: MiroMetadata = MiroMetadata(isClearedForCatalogueAPI = true),
    version: Int = 1): NotificationMessage = {

    val hybridRecord = createHybridRecordWith(miroRecord, miroMetadata, version)
    createNotificationMessageWith(hybridRecord)
  }

  def createHybridRecordWith(
    miroRecord: MiroRecord = createMiroRecord,
    miroMetadata: MiroMetadata = MiroMetadata(isClearedForCatalogueAPI = true),
    version: Int = 1,
    id: String = Random.alphanumeric take 10 mkString): HybridRecord = {

    store.put(Version(id, version))(HybridStoreEntry(miroRecord, miroMetadata))
    HybridRecord(
      id = id,
      version = version,
      location = ObjectLocation("namespace", "path")
    )
  }
}
