package uk.ac.wellcome.platform.transformer.miro.fixtures

import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global
//import java.io.ByteArrayInputStream

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.models.work.internal.TransformedBaseWork
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.models.MiroMetadata
import uk.ac.wellcome.platform.transformer.miro.services.{MiroVHSRecordReceiver, HybridRecord}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.fixtures.TestWith

import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture

import uk.ac.wellcome.messaging.fixtures.SNS.Topic
//import uk.ac.wellcome.messaging.fixtures.SNS
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig}

import uk.ac.wellcome.storage.{ObjectLocation, Version}
import uk.ac.wellcome.storage.store.{
  HybridIndexedStoreEntry,
  HybridStore,
  HybridStoreEntry
}
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.store.memory.{
  MemoryStore,
  MemoryStreamStore,
  MemoryTypedStore
}
import uk.ac.wellcome.storage.streaming.Codec._
//import uk.ac.wellcome.storage.streaming.InputStreamWithLengthAndMetadata

trait MiroVHSRecordReceiverFixture extends BigMessagingFixture with MiroRecordGenerators {

  val memoryIndexedStore =
    new MemoryStore[
      Version[String, Int],
      HybridIndexedStoreEntry[String, MiroMetadata]](Map.empty)

  implicit val memoryStreamStore = MemoryStreamStore[String]()

  implicit val hybridStore =
    new HybridStore[
      Version[String, Int],
      String,
      MiroRecord,
      MiroMetadata] {
      override implicit val indexedStore = memoryIndexedStore
      override implicit val typedStore
        : MemoryTypedStore[String, MiroRecord] =
        new MemoryTypedStore[String, MiroRecord](Map.empty)
      override def createTypeStoreId(id: Version[String, Int]): String =
        s"${id.id}/${id.version}"
    }

  def withMiroVHSRecordReceiver[R](topic: Topic, bucket: Bucket)(
    testWith: TestWith[MiroVHSRecordReceiver[SNSConfig], R]): R = {
    withSqsBigMessageSender[TransformedBaseWork, R](bucket, topic, snsClient) { msgSender =>
      val recordReceiver = new MiroVHSRecordReceiver(msgSender, hybridStore)
      testWith(recordReceiver)
    }
  }

  def createHybridRecordNotificationWith(
    miroRecord: MiroRecord = createMiroRecord,
    version: Int = 1): NotificationMessage = {

    val hybridRecord = createHybridRecordWith(miroRecord, version)
    createNotificationMessageWith(hybridRecord)
  }

  def createHybridRecordWith(
    miroRecord: MiroRecord = createMiroRecord,
    version: Int = 1,
    id: String = Random.alphanumeric take 10 mkString): HybridRecord = {

    val miroMetadata = MiroMetadata(isClearedForCatalogueAPI = true)
    hybridStore.put(Version(id, version))(
      HybridStoreEntry(miroRecord, miroMetadata))
    HybridRecord(
      id = id,
      version = version,
      location = ObjectLocation("namespace", "path")
    )
  }

  /*
  def createCorruptedHybridRecord(
    version: Int = 1,
    id: String = Random.alphanumeric take 10 mkString): HybridRecord = {

    val typeId = s"${id}/${version}"
    val stream = new ByteArrayInputStream("{\"x\": 1}".getBytes)
    memoryIndexedStore.put(Version(id, version))(
      HybridIndexedStoreEntry(typeId, EmptyMetadata()))
    memoryStreamStore.put(typeId)(
      new InputStreamWithLengthAndMetadata(stream, 8L, Map.empty))
    HybridRecord(
      id = id,
      version = version,
      location = ObjectLocation("namespace", "path")
    )
  }
  */

 /*
  def createMiroVHSRecordNotificationMessageWith(
    miroRecord: MiroRecord = createMiroRecord,
    bucket: Bucket,
    version: Int = 1
  ): NotificationMessage = {
    val hybridRecord = createHybridRecordWith(
      miroRecord,
      version = version,
      bucket = bucket
    )

    val miroMetadata = MiroMetadata(isClearedForCatalogueAPI = true)

    // Yes creating the JSON here manually is a bit icky, but it means this is
    // a fixed, easy-to-read output, and saves us mucking around with Circe encoders.
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
  */
}
