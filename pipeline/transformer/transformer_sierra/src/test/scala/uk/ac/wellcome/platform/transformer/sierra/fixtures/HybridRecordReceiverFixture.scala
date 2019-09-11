package uk.ac.wellcome.platform.transformer.sierra.fixtures

import scala.util.Random
import com.amazonaws.services.sns.AmazonSNS

import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.sierra.services.{
  HybridRecord,
  UpcomingHybridRecordReceiver,
  BackwardsCompatHybridRecordReceiver,
  BackwardsCompatObjectLocation
}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._

import uk.ac.wellcome.models.transformable.SierraTransformable

import uk.ac.wellcome.bigmessaging.fixtures.{VHSFixture, BigMessagingFixture}
import uk.ac.wellcome.bigmessaging.EmptyMetadata
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}

import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.{HybridStoreEntry, Store, TypedStoreEntry}
import uk.ac.wellcome.storage.store.memory.MemoryStore
import uk.ac.wellcome.storage.{
  ObjectLocation,
  StoreReadError,
  StoreWriteError,
  Version
}

trait BackwardsCompatHybridRecordReceiverFixture extends BigMessagingFixture {

  type SierraStore = Store[ObjectLocation, TypedStoreEntry[SierraTransformable]]

  def withHybridRecordReceiver[R](store: SierraStore,
                                  topic: Topic,
                                  bucket: Bucket,
                                  snsClient: AmazonSNS = snsClient)(
    testWith: TestWith[BackwardsCompatHybridRecordReceiver[SNSConfig], R]): R =
    withSqsBigMessageSender[TransformedBaseWork, R](bucket, topic, snsClient) {
      msgSender =>
        val recorderReciver = new BackwardsCompatHybridRecordReceiver(msgSender, store)
        testWith(recorderReciver)
    }

  def createHybridRecordNotificationWith(
    sierraTransformable: SierraTransformable,
    store: SierraStore,
    namespace: String = "test",
    version: Int = 1): NotificationMessage = {

    val hybridRecord = createHybridRecordWith(
      sierraTransformable,
      store,
      namespace = namespace,
      version = version
    )
    createNotificationMessageWith(
      message = hybridRecord
    )
  }

  def createHybridRecordWith(
    sierraTransformable: SierraTransformable,
    store: SierraStore,
    version: Int = 1,
    namespace: String = "test",
    id: String = Random.alphanumeric take 10 mkString): HybridRecord[BackwardsCompatObjectLocation] = {
    val location = ObjectLocation(namespace = namespace, path = id)
    store.put(location)(TypedStoreEntry(sierraTransformable, Map.empty))
    HybridRecord(
      id = id,
      version = version,
      location = BackwardsCompatObjectLocation(location.namespace, location.path)
    )
  }

  def withSierraStore[R](testWith: TestWith[SierraStore, R]): R =
    testWith(new MemoryStore(Map.empty))

  def withBrokenSierraStore[R](testWith: TestWith[SierraStore, R]): R =
    testWith(BrokenSierraStore)

  object BrokenSierraStore extends SierraStore {
    def put(id: ObjectLocation)(entry: TypedStoreEntry[SierraTransformable]): WriteEither =
      Left(StoreWriteError(new Error("BOOM!")))
    def get(id: ObjectLocation): ReadEither =
      Left(StoreReadError(new Error("BOOM!")))
  }
}

trait HybridRecordReceiverFixture extends VHSFixture[SierraTransformable] {

  def withHybridRecordReceiver[R](vhs: VHS,
                                  topic: Topic,
                                  bucket: Bucket,
                                  snsClient: AmazonSNS = snsClient)(
    testWith: TestWith[UpcomingHybridRecordReceiver[SNSConfig], R]): R =
    withSqsBigMessageSender[TransformedBaseWork, R](bucket, topic, snsClient) {
      msgSender =>
        val recorderReciver = new UpcomingHybridRecordReceiver(msgSender, vhs)
        testWith(recorderReciver)
    }

  def createHybridRecordNotificationWith(
    sierraTransformable: SierraTransformable,
    vhs: VHS,
    version: Int = 1): NotificationMessage = {

    val hybridRecord = createHybridRecordWith(
      sierraTransformable,
      vhs,
      version = version
    )
    createNotificationMessageWith(
      message = hybridRecord
    )
  }

  def createHybridRecordWith(
    sierraTransformable: SierraTransformable,
    vhs: VHS,
    version: Int = 1,
    id: String = Random.alphanumeric take 10 mkString): HybridRecord[ObjectLocation] = {

    vhs.put(Version(id, version))(
      HybridStoreEntry(sierraTransformable, EmptyMetadata()))
    HybridRecord(
      id = id,
      version = version,
      location = ObjectLocation("namespace.doesnt.matter", "path/is/irrelevant")
    )
  }
}
