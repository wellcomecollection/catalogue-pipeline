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
import uk.ac.wellcome.storage.store.{Store, HybridStoreEntry}
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.memory.MemoryStore

trait HybridRecordReceiverFixture extends BigMessagingFixture with SNS {

  type StoreEntry = HybridStoreEntry[SierraTransformable, EmptyMetadata]

  type SierraTransformableStore = Store[Version[String, Int], StoreEntry]

  implicit val sierraTransformableStore : SierraTransformableStore =
    new MemoryStore[Version[String, Int], StoreEntry](Map.empty)

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
        store = sierraTransformableStore
      )

      testWith(recordReceiver)
    }

  def createHybridRecordWith(
    sierraTransformable: SierraTransformable,
    version: Int = 1,
    id: String = Random.alphanumeric take 10 mkString): HybridRecord = {

    sierraTransformableStore.put(Version(id, version))(
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
