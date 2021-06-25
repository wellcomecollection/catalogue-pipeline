package weco.pipeline.sierra_reader.sink

import akka.Done
import akka.stream.scaladsl.{Sink, Source}
import io.circe.Json
import io.circe.parser._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.compatible.Assertion
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.akka.fixtures.Akka
import weco.fixtures.TestWith
import weco.storage.fixtures.S3Fixtures
import weco.storage.fixtures.S3Fixtures.Bucket
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.s3.S3TypedStore

import scala.concurrent.Future

class SequentialS3SinkTest
    extends AnyFunSpec
    with Matchers
    with S3Fixtures
    with Akka
    with BeforeAndAfterAll
    with ScalaFutures
    with IntegrationPatience {

  private def withSink(bucket: Bucket, keyPrefix: String, offset: Int = 0)(
    testWith: TestWith[Sink[(Json, Long), Future[Done]], Assertion]) = {
    val sink = SequentialS3Sink(
      S3TypedStore[String],
      bucketName = bucket.name,
      keyPrefix = keyPrefix,
      offset = offset
    )

    testWith(sink)
  }

  it("puts a single JSON in S3") {
    val json = parse(s"""{"hello": "world"}""").right.get

    withLocalS3Bucket { bucket =>
      withMaterializer { implicit materializer =>
        withSink(bucket = bucket, keyPrefix = "testA_") { sink =>
          val futureDone = Source
            .single(json)
            .zipWithIndex
            .runWith(sink)

          whenReady(futureDone) { _ =>
            val keys = listKeysInBucket(bucket = bucket)
            keys should have size 1
            keys.head shouldBe "testA_0000.json"

            getJsonFromS3(bucket, "testA_0000.json") shouldBe json
          }
        }
      }
    }
  }

  it("puts multiple JSON bodies into S3") {
    val json0 = parse(s"""{"red": "orange"}""").right.get
    val json1 = parse(s"""{"orange": "yellow"}""").right.get
    val json2 = parse(s"""{"yellow": "green"}""").right.get

    withLocalS3Bucket { bucket =>
      withMaterializer { implicit materializer =>
        withSink(bucket = bucket, keyPrefix = "testB_") { sink =>
          val futureDone = Source(List(json0, json1, json2)).zipWithIndex
            .runWith(sink)

          whenReady(futureDone) { _ =>
            val keys = listKeysInBucket(bucket = bucket)
            keys should have size 3
            keys shouldBe List(
              "testB_0000.json",
              "testB_0001.json",
              "testB_0002.json")

            getJsonFromS3(bucket, "testB_0000.json") shouldBe json0
            getJsonFromS3(bucket, "testB_0001.json") shouldBe json1
            getJsonFromS3(bucket, "testB_0002.json") shouldBe json2
          }
        }
      }
    }
  }

  it("uses the offset if provided") {
    val json0 = parse(s"""{"red": "orange"}""").right.get
    val json1 = parse(s"""{"orange": "yellow"}""").right.get
    val json2 = parse(s"""{"yellow": "green"}""").right.get

    withLocalS3Bucket { bucket =>
      withMaterializer { implicit materializer =>
        withSink(bucket = bucket, keyPrefix = "testC_", offset = 3) { sink =>
          val futureDone = Source(List(json0, json1, json2)).zipWithIndex
            .runWith(sink)

          whenReady(futureDone) { _ =>
            val keys = listKeysInBucket(bucket = bucket)
            keys shouldBe List(
              "testC_0003.json",
              "testC_0004.json",
              "testC_0005.json")

            getJsonFromS3(bucket, "testC_0003.json") shouldBe json0
            getJsonFromS3(bucket, "testC_0004.json") shouldBe json1
            getJsonFromS3(bucket, "testC_0005.json") shouldBe json2
          }
        }
      }
    }
  }

  def getJsonFromS3(bucket: Bucket, key: String): Json =
    getJsonFromS3(S3ObjectLocation(bucket.name, key))
}
