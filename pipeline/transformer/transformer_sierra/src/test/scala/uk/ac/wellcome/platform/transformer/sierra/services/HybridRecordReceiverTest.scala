package uk.ac.wellcome.platform.transformer.sierra.services

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.PublishRequest
import io.circe.ParsingFailure
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.SierraTransformable._
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal.{
  TransformedBaseWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.transformer.sierra.fixtures.{
  HybridRecordReceiverFixture,
  SierraTransformableStoreFixture
}

import scala.util.{Random, Try}

import uk.ac.wellcome.storage.ObjectLocation


class HybridRecordReceiverTest
    extends FunSpec
    with Matchers
    with BigMessagingFixture
    with Eventually
    with HybridRecordReceiverFixture
    with IntegrationPatience
    with MockitoSugar
    with ScalaFutures
    with SierraGenerators
    with SierraTransformableStoreFixture
    with WorksGenerators {

  case class TestException(message: String) extends Exception(message)

  def transformToWork(transformable: SierraTransformable, version: Int) =
    Try(createUnidentifiedWorkWith(version = version))
  def failingTransformToWork(transformable: SierraTransformable, version: Int) =
    Try(throw TestException("BOOOM!"))

  it("receives a message and sends it to SNS client") {
    withLocalSnsTopic { topic =>
      withLocalS3Bucket { bucket =>
        val sqsMessage = createHybridRecordNotificationWith(
          createSierraTransformable,
          bucket = bucket
        )

        withHybridRecordReceiver(topic, bucket) { recordReceiver =>
          val future =
            recordReceiver.receiveMessage(sqsMessage, transformToWork)

          whenReady(future) { _ =>
            val works = getMessages[TransformedBaseWork](topic)
            works.size should be >= 1

            works.map { work =>
              work shouldBe a[UnidentifiedWork]
            }
          }
        }
      }
    }
  }

  it("receives a message and adds the version to the transformed work") {
    val version = 5

    withLocalSnsTopic { topic =>
      withLocalS3Bucket { bucket =>
        val notification = createHybridRecordNotificationWith(
          createSierraTransformable,
          version = version,
          bucket = bucket
        )

        withHybridRecordReceiver(topic, bucket) { recordReceiver =>
          val future =
            recordReceiver.receiveMessage(notification, transformToWork)

          whenReady(future) { _ =>
            val works = getMessages[TransformedBaseWork](topic)
            works.size should be >= 1

            works.map { actualWork =>
              actualWork shouldBe a[UnidentifiedWork]
              val unidentifiedWork = actualWork.asInstanceOf[UnidentifiedWork]
              unidentifiedWork.version shouldBe version
            }
          }
        }
      }
    }
  }

  it("returns a failed future if it's unable to parse the SQS message") {
    withLocalSnsTopic { topic =>
      withLocalS3Bucket { bucket =>
        val key = randomAlphanumeric(10)
        s3Client.putObject(bucket.name, key, "not a JSON string")

        val hybridRecord = HybridRecord(
          id = "testId",
          version = 1,
          location = ObjectLocation(bucket.name, key)
        )
        val invalidSqsMessage = createNotificationMessageWith(
          message = hybridRecord
        )

        withHybridRecordReceiver(topic, bucket) { recordReceiver =>
          val future =
            recordReceiver.receiveMessage(invalidSqsMessage, transformToWork)

          whenReady(future.failed) { x =>
            x shouldBe a[ParsingFailure]
          }
        }
      }
    }
  }

  it("fails if it can't parse a HybridRecord from SNS") {
    withLocalSnsTopic { topic =>
      withLocalS3Bucket { bucket =>
        val invalidSqsMessage = createNotificationMessageWith(
          message = Random.alphanumeric take 50 mkString
        )

        withHybridRecordReceiver(topic, bucket) { recordReceiver =>
          val future =
            recordReceiver.receiveMessage(invalidSqsMessage, transformToWork)

          whenReady(future.failed) {
            _ shouldBe a[JsonDecodingError]
          }
        }
      }
    }
  }

  it("fails if it's unable to perform a transformation") {
    withLocalSnsTopic { topic =>
      withLocalS3Bucket { bucket =>
        val failingSqsMessage = createHybridRecordNotificationWith(
          createSierraTransformable,
          bucket = bucket
        )

        withHybridRecordReceiver(topic, bucket) { recordReceiver =>
          val future =
            recordReceiver.receiveMessage(
              failingSqsMessage,
              failingTransformToWork)

          whenReady(future.failed) {
            _ shouldBe a[TestException]
          }
        }
      }
    }
  }

  it("fails if it's unable to publish the work") {
    withLocalSnsTopic { topic =>
      withLocalS3Bucket { bucket =>
        val message = createHybridRecordNotificationWith(
          createSierraTransformable,
          bucket = bucket
        )

        withHybridRecordReceiver(topic, bucket, mockSnsClientFailPublishMessage) {
          recordReceiver =>
            val future = recordReceiver.receiveMessage(message, transformToWork)

            whenReady(future.failed) {
              _.getMessage should be("Failed publishing message")
            }
        }
      }
    }
  }

  private def mockSnsClientFailPublishMessage: AmazonSNS = {
    val mockSNSClient = mock[AmazonSNS]
    when(mockSNSClient.publish(any[PublishRequest]))
      .thenThrow(new RuntimeException("Failed publishing message"))
    mockSNSClient
  }
}
