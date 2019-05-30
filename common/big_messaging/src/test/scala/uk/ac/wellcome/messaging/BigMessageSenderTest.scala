package uk.ac.wellcome.messaging

import io.circe.Encoder
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.memory.{MemoryBigMessageSender, MemoryMessageSender}
import uk.ac.wellcome.messaging.message.{InlineNotification, MessageNotification, RemoteNotification}
import uk.ac.wellcome.storage._
import uk.ac.wellcome.storage.memory.MemoryObjectStore
import uk.ac.wellcome.storage.streaming.CodecInstances._

import scala.util.{Failure, Success}

class BigMessageSenderTest extends FunSpec with Matchers {
  case class Shape(colour: String, sides: Int)

  val redSquare = Shape(colour = "red", sides = 4)

  it("sends an inline notification if the message is small") {
    val sender = new MemoryBigMessageSender[Shape](
      maxSize = 10000000
    )

    sender.sendT(redSquare) shouldBe a[Success[_]]

    sender.messages should have size 1
    val notification = fromJson[MessageNotification](sender.messages.head.body).get
    notification shouldBe a[InlineNotification]
    val body = notification.asInstanceOf[InlineNotification]
    fromJson[Shape](body.jsonString).get shouldBe redSquare
  }

  it("sends a remote notification is the message is too big") {
    val sender = new MemoryBigMessageSender[Shape](
      maxSize = 1
    )

    sender.sendT(redSquare) shouldBe a[Success[_]]

    sender.messages should have size 1
    val notification = fromJson[MessageNotification](sender.messages.head.body).get
    notification shouldBe a[RemoteNotification]
    val location = notification.asInstanceOf[RemoteNotification].location

    sender.objectStore.get(location) shouldBe Right(redSquare)
  }

  it("gives distinct keys when sending the same message twice") {
    val sender = new MemoryBigMessageSender[Shape](
      maxSize = 1
    )

    sender.sendT(redSquare) shouldBe a[Success[_]]
    Thread.sleep(2000)
    sender.sendT(redSquare) shouldBe a[Success[_]]

    sender.messages should have size 2

    val locations =
      sender.messages
        .map { msg => fromJson[MessageNotification](msg.body).get }
        .map { _.asInstanceOf[RemoteNotification].location }

    locations.distinct should have size 2
  }

  it("uses the namespace when storing messages in the store") {
    val sender = new MemoryBigMessageSender[Shape](
      maxSize = 1,
      storeNamespace = "squares"
    )

    sender.sendT(redSquare) shouldBe a[Success[_]]

    sender.messages should have size 1
    val notification = fromJson[MessageNotification](sender.messages.head.body).get
    notification shouldBe a[RemoteNotification]
    val location = notification.asInstanceOf[RemoteNotification].location

    location.namespace shouldBe "squares"
  }

  it("uses the destination as a key prefix") {
    val sender = new MemoryBigMessageSender[Shape](
      maxSize = 1,
      messageDestination = "squares"
    )

    sender.sendT(redSquare) shouldBe a[Success[_]]

    sender.messages should have size 1
    val notification = fromJson[MessageNotification](sender.messages.head.body).get
    notification shouldBe a[RemoteNotification]
    val location = notification.asInstanceOf[RemoteNotification].location

    location.key should startWith("squares/")
  }

  it("fails if it the message sender has a problem") {
    val sender = new MemoryBigMessageSender[Shape]() {
      override val messageSender = new MemoryMessageSender() {
        override def sendT[T](t: T)(implicit encoder: Encoder[T]) =
          Failure(new Throwable("BOOM!"))
      }
    }

    val result = sender.sendT(redSquare)

    result shouldBe a[Failure[_]]
    val err = result.failed.get
    err shouldBe a[Throwable]
    err.getMessage shouldBe "BOOM!"

    sender.messages shouldBe empty
  }

  it("fails if it cannot put a remote object in the store") {
    val sender = new MemoryBigMessageSender[Shape](
      maxSize = 1
    ) {
      override val objectStore: ObjectStore[Shape] = new MemoryObjectStore[Shape]() {
        override def put(namespace: String)(input: Shape, keyPrefix: KeyPrefix, keySuffix: KeySuffix, userMetadata: Map[String, String]): Either[BackendWriteError, ObjectLocation] = Left(BackendWriteError(new Throwable("BOOM!")))
      }
    }

    val result = sender.sendT(redSquare)

    result shouldBe a[Failure[_]]
    val err = result.failed.get
    err shouldBe a[Throwable]
    err.getMessage shouldBe "BOOM!"

    sender.messages shouldBe empty
  }
}
