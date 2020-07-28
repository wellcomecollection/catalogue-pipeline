package uk.ac.wellcome.bigmessaging

import io.circe.Decoder
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.message.{
  InlineNotification,
  RemoteNotification
}
import uk.ac.wellcome.bigmessaging.s3.S3BigMessageSender
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.sns.SNSConfig
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.store.s3.S3TypedStore
import uk.ac.wellcome.storage.streaming.Codec

import scala.util.Success

class BigMessageIntegrationTest
    extends AnyFunSpec
    with Matchers
    with BigMessagingFixture {
  case class Shape(colour: String, sides: Int)

  val yellowPentagon = Shape(colour = "yellow", sides = 5)

  def withPair[R](topic: Topic, maxMessageSize: Int)(
    testWith: TestWith[(BigMessageSender[SNSConfig], BigMessageReader[Shape]),
                       R])(implicit decoderS: Decoder[Shape],
                           codec: Codec[Shape]): R =
    withLocalS3Bucket { bucket =>
      val sender = S3BigMessageSender(
        bucketName = bucket.name,
        snsConfig = createSNSConfigWith(topic),
        maxMessageSize = maxMessageSize
      )

      val reader = new BigMessageReader[Shape] {
        override val store: Store[ObjectLocation, Shape] =
          S3TypedStore[Shape]
        override implicit val decoder: Decoder[Shape] = decoderS
      }

      testWith((sender, reader))
    }

  it("handles an inline notification") {
    withLocalSnsTopic { topic =>
      withPair(topic, maxMessageSize = 1000) {
        case (sender, reader) =>
          sender.sendT(yellowPentagon) shouldBe a[Success[_]]

          val notification = getMessages[InlineNotification](topic).head
          reader.read(notification) shouldBe Success(yellowPentagon)
      }
    }
  }

  it("handles a remote message") {
    withLocalSnsTopic { topic =>
      withPair(topic, maxMessageSize = 1) {
        case (sender, reader) =>
          sender.sendT(yellowPentagon) shouldBe a[Success[_]]

          val notification = getMessages[RemoteNotification](topic).head
          reader.read(notification) shouldBe Success(yellowPentagon)
      }
    }
  }
}
