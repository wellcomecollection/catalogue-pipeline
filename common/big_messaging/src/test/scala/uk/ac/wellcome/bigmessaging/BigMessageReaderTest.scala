package uk.ac.wellcome.bigmessaging

import io.circe.Decoder
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.bigmessaging.message.{
  InlineNotification,
  RemoteNotification
}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.exceptions.JsonDecodingError
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Store
import uk.ac.wellcome.storage.store.memory.MemoryStore

import scala.util.{Failure, Success}

class BigMessageReaderTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with S3ObjectLocationGenerators {
  case class Shape(colour: String, sides: Int)

  val blueTriangle = Shape(colour = "blue", sides = 3)

  def createReader(shapeStore: Store[S3ObjectLocation, Shape] = new MemoryStore(
                     Map.empty))(
    implicit decoderS: Decoder[Shape]): BigMessageReader[Shape] =
    new BigMessageReader[Shape] {
      override val store: Store[S3ObjectLocation, Shape] =
        shapeStore
      override implicit val decoder: Decoder[Shape] = decoderS
    }

  it("reads a large message from the object store") {
    val store = new MemoryStore(Map.empty[S3ObjectLocation, Shape])
    val reader = createReader(store)
    val objectLocation = createS3ObjectLocation

    store.put(objectLocation)(blueTriangle)

    val notification = RemoteNotification(objectLocation)

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
      location = S3ObjectLocation("does-not", "exist")
    )

    val result = reader.read(notification)

    result shouldBe a[Failure[_]]
    val err = result.failed.get
    err shouldBe a[Throwable]
    err.getMessage shouldBe "Nothing at s3://does-not/exist"
  }
}
