package uk.ac.wellcome.bigmessaging

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.bigmessaging.message.{
  InlineNotification,
  MessageNotification,
  RemoteNotification
}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SNS.Topic

import scala.util.{Failure, Success}

class BigMessageSenderTest
    extends AnyFunSpec
    with Matchers
    with BigMessagingFixture {
  case class Shape(colour: String, sides: Int)

  val redSquare = Shape(colour = "red", sides = 4)

  it("sends an inline notification if the message is small") {
    withLocalS3Bucket { bucket =>
      withLocalSnsTopic { topic =>
        withSqsBigMessageSender(bucket, topic, maxMessageSize = Int.MaxValue) {
          sender =>
            sender.sendT(redSquare) shouldBe a[Success[_]]

            val messages: Seq[MessageNotification] =
              listMessagesReceivedFromSNS(topic)
                .map { msg => fromJson[MessageNotification](msg.message).get }

            messages should have size 1
            messages.head shouldBe a[InlineNotification]

            val notification = messages.head.asInstanceOf[InlineNotification]
            fromJson[Shape](notification.jsonString).get shouldBe redSquare
        }
      }
    }
  }

  it("sends a remote notification is the message is too big") {
    withLocalS3Bucket { bucket =>
      withLocalSnsTopic { topic =>
        withSqsBigMessageSender(bucket, topic, maxMessageSize = 1) { sender =>
          sender.sendT(redSquare) shouldBe a[Success[_]]

          val messages: Seq[MessageNotification] =
            listMessagesReceivedFromSNS(topic)
              .map { msg => fromJson[MessageNotification](msg.message).get }

          messages should have size 1
          messages.head shouldBe a[RemoteNotification]

          val location = messages.head.asInstanceOf[RemoteNotification].location

          getObjectFromS3[Shape](location) shouldBe redSquare
        }
      }
    }
  }

  it("gives distinct keys when sending the same message twice") {
    withLocalS3Bucket { bucket =>
      withLocalSnsTopic { topic =>
        withSqsBigMessageSender(bucket, topic, maxMessageSize = 1) { sender =>
          sender.sendT(redSquare) shouldBe a[Success[_]]
          Thread.sleep(2000)
          sender.sendT(redSquare) shouldBe a[Success[_]]

          val messages: Seq[RemoteNotification] =
            listMessagesReceivedFromSNS(topic)
              .map { msg => fromJson[RemoteNotification](msg.message).get }

          messages.map { _.location }.distinct should have size 2
        }
      }
    }
  }

  it("uses the namespace when storing messages in the store") {
    withLocalS3Bucket { bucket =>
      withLocalSnsTopic { topic =>
        withSqsBigMessageSender(bucket, topic, maxMessageSize = 1) { sender =>
          sender.sendT(redSquare) shouldBe a[Success[_]]

          val messages: Seq[RemoteNotification] =
            listMessagesReceivedFromSNS(topic)
              .map { msg => fromJson[RemoteNotification](msg.message).get }

          messages.head.location.namespace shouldBe bucket.name
        }
      }
    }
  }

  it("fails if it the message sender has a problem") {
    val badTopic = Topic("arn:::does-not-exist")

    withLocalS3Bucket { bucket =>
      withSqsBigMessageSender(bucket, badTopic, maxMessageSize = 1) { sender =>
        sender.sendT(redSquare) shouldBe a[Failure[_]]
      }
    }
  }

  it("fails if it cannot put a remote object in the store") {
    val badBucket = createBucket

    withLocalSnsTopic { topic =>
      withSqsBigMessageSender(badBucket, topic, maxMessageSize = 1) { sender =>
        sender.sendT(redSquare) shouldBe a[Failure[_]]

        getMessages[MessageNotification](topic) shouldBe empty
      }
    }
  }
}
