package uk.ac.wellcome.platform.transformer.sierra.fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import com.amazonaws.services.sns.AmazonSNS

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.sierra.services.{HybridRecordReceiver, HybridRecord, EmptyMetadata}
import uk.ac.wellcome.fixtures.TestWith

import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SNS
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.sns.{SNSConfig, NotificationMessage}

import uk.ac.wellcome.storage.{ObjectLocation, Version}
import uk.ac.wellcome.storage.store.{Store, HybridStore, HybridStoreEntry, HybridIndexedStoreEntry}
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.memory.{MemoryStore, MemoryTypedStore, MemoryStreamStore}
import uk.ac.wellcome.models.transformable.SierraTransformable._
import uk.ac.wellcome.storage.streaming.Codec._

trait HybridRecordReceiverFixture extends BigMessagingFixture with SNS {

  type StoreEntry = HybridStoreEntry[SierraTransformable, EmptyMetadata]

  type SierraTransformableStore = Store[Version[String, Int], StoreEntry]

  type IndexEntry = HybridIndexedStoreEntry[String, EmptyMetadata]

  implicit val memoryStreamStore: MemoryStreamStore[String] =
    MemoryStreamStore[String]()

  implicit val hybridStore : SierraTransformableStore =
    new HybridStore[Version[String, Int], String, SierraTransformable, EmptyMetadata] {
      override implicit val indexedStore =
        new MemoryStore[Version[String, Int], IndexEntry](
          Map.empty)
      override implicit val typedStore : MemoryTypedStore[String, SierraTransformable] =
        new MemoryTypedStore[String, SierraTransformable](
          Map.empty)
      override def createTypeStoreId(id: Version[String, Int]): String =
        s"${id.id}/${id.version}"
    }

  def withHybridRecordReceiver[R](
    topic: Topic,
    bucket: Bucket,
    snsClient: AmazonSNS = snsClient
  )(testWith: TestWith[HybridRecordReceiver[SNSConfig], R]): R =
    withSqsBigMessageSender[TransformedBaseWork, R](
      bucket,
      topic) { msgSender =>
      val recordReceiver = new HybridRecordReceiver(
        msgSender = msgSender,
        store = hybridStore
      )

      testWith(recordReceiver)
    }

  def createHybridRecordWith(
    sierraTransformable: SierraTransformable,
    version: Int = 1,
    id: String = Random.alphanumeric take 10 mkString): HybridRecord = {

    hybridStore.put(
      Version(id, version))(
      HybridStoreEntry(sierraTransformable, EmptyMetadata()))
    HybridRecord(
      id = id,
      version = version,
      location = ObjectLocation("namespace", "path")
    )
  }

  def createHybridRecordNotificationWith(
    sierraTransformable: SierraTransformable,
    version: Int = 1): NotificationMessage = {

    val hybridRecord = createHybridRecordWith(
      sierraTransformable,
      version = version
    )
    createNotificationMessageWith(
      message = hybridRecord
    )
  }
}
