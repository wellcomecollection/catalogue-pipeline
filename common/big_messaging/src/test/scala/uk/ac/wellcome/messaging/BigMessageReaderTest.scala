package uk.ac.wellcome.messaging

import io.circe.Decoder
import org.scalatest.{EitherValues, FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.messaging.message.{InlineNotification, RemoteNotification}
import uk.ac.wellcome.storage.{ObjectLocation, ObjectStore}
import uk.ac.wellcome.storage.memory.MemoryObjectStore
import uk.ac.wellcome.storage.streaming.CodecInstances._

import scala.util.{Failure, Success}

class BigMessageReaderTest extends FunSpec with Matchers with EitherValues {
  case class Shape(colour: String, sides: Int)

  val blueTriangle = Shape(colour = "blue", sides = 3)

  def createReader(store: ObjectStore[Shape] = new MemoryObjectStore[Shape]())(implicit decoderS: Decoder[Shape]): BigMessageReader[Shape] =
    new BigMessageReader[Shape] {
      override val objectStore: ObjectStore[Shape] = store
      override implicit val decoder: Decoder[Shape] = decoderS
    }

  it("reads a large message from the object store") {
    val store = new MemoryObjectStore[Shape]()

    val reader = createReader(store)

    val location = store.put(namespace = "shapes")(blueTriangle).right.get
    val notification = RemoteNotification(location)

    reader.read(notification) shouldBe Success(blueTriangle)
  }

  it("reads an inline notification") {
    val reader = createReader()

    val notification = InlineNotification(toJson(blueTriangle).get)

    reader.read(notification) shouldBe Success(blueTriangle)
  }

  it("fails if the inline notification contains malformed JSON") {
    val reader = createReader()

    val notification = InlineNotification("xyz")

    val result = reader.read(notification)

    result shouldBe a[Failure[_]]
    val err = result.failed.get
    err shouldBe a[JsonDecodingError]
  }

  it("fails if the remote notification refers to a non-existent location") {
    val reader = createReader()

    val notification = RemoteNotification(
      location = ObjectLocation("does-not", "exist")
    )

    val result = reader.read(notification)

    result shouldBe a[Failure[_]]
    val err = result.failed.get
    err shouldBe a[Throwable]
    err.getMessage shouldBe "Nothing at does-not/exist"
  }
}
