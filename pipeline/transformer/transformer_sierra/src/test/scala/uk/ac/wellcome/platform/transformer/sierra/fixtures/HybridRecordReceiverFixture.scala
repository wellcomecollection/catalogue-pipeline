package uk.ac.wellcome.platform.transformer.sierra.fixtures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import io.circe.Encoder
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.s3.AmazonS3

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
import uk.ac.wellcome.storage.store.HybridStore
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket

trait HybridRecordReceiverFixture extends BigMessagingFixture with SNS {

  def withHybridRecordReceiver[R](
    topic: Topic,
    bucket: Bucket,
    snsClient: AmazonSNS = snsClient
  )(testWith: TestWith[HybridRecordReceiver[SNSConfig], R])(
    implicit store: HybridStore[
      Version[String, Int],
      ObjectLocation,
      SierraTransformable,
      EmptyMetadata]): R =
    withSqsBigMessageSender[TransformedBaseWork, R](
      bucket,
      topic) { msgSender =>
      val recordReceiver = new HybridRecordReceiver(
        msgSender = msgSender,
        store = store
      )

      testWith(recordReceiver)
    }

  def createHybridRecordWith[T](
    t: T,
    version: Int = 1,
    s3Client: AmazonS3 = s3Client,
    bucket: Bucket)(implicit encoder: Encoder[T]): HybridRecord = {
    val s3key = Random.alphanumeric take 10 mkString
    val content = toJson(t).get
    s3Client.putObject(bucket.name, s3key, content)

    HybridRecord(
      id = Random.alphanumeric take 10 mkString,
      version = version,
      location = ObjectLocation(namespace = bucket.name, path = s3key)
    )
  }

  /** Store an object in S3 and create the HybridRecordNotification that should be sent to SNS. */
  def createHybridRecordNotificationWith[T](
    t: T,
    version: Int = 1,
    s3Client: AmazonS3 = s3Client,
    bucket: Bucket)(implicit encoder: Encoder[T]): NotificationMessage = {
    val hybridRecord = createHybridRecordWith[T](
      t,
      version = version,
      s3Client = s3Client,
      bucket = bucket
    )

    createNotificationMessageWith(
      message = hybridRecord
    )
  }
}
