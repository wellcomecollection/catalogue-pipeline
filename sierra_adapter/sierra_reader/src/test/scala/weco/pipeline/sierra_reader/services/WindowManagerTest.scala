package weco.pipeline.sierra_reader.services

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.AmazonS3Exception
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.json.JsonUtil._
import weco.pipeline.sierra_reader.config.models.ReaderConfig
import weco.pipeline.sierra_reader.exceptions.SierraReaderException
import weco.fixtures.TestWith
import weco.storage.fixtures.S3Fixtures
import weco.storage.fixtures.S3Fixtures.Bucket
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.pipeline.sierra_reader.models.WindowStatus
import weco.sierra.models.identifiers.{SierraBibNumber, SierraRecordTypes}

class WindowManagerTest
    extends AnyFunSpec
    with Matchers
    with S3Fixtures
    with ScalaFutures
    with IntegrationPatience
    with SierraRecordGenerators {

  // TODO: We're overriding these values while scala-libs is still tied to scality/s3server,
  // but when we update it to use localstack, we can remove these.
  // See https://github.com/wellcomecollection/platform/issues/5547
  override val s3Port: Int = 4566
  override implicit val s3Client: AmazonS3 =
    createS3ClientWithEndpoint(s"http://localhost:$s3Port")

  private def withWindowManager[R](bucket: Bucket)(
    testWith: TestWith[WindowManager, R]) = {
    val windowManager = new WindowManager(
      s3Config = createS3ConfigWith(bucket),
      readerConfig = ReaderConfig(
        recordType = SierraRecordTypes.bibs,
        fields = "title"
      )
    )

    testWith(windowManager)
  }

  val startDateTime = "2013-01-01T00:00:00+00:00"
  val endDateTime = "2014-01-01T00:00:00+00:00"
  it("returns an empty ID and offset 0 if there isn't a window in progress") {
    withLocalS3Bucket { bucket =>
      withWindowManager(bucket) { windowManager =>
        val result =
          windowManager.getCurrentStatus(s"[$startDateTime,$endDateTime]")

        whenReady(result) {
          _ shouldBe WindowStatus(id = None, offset = 0)
        }
      }
    }
  }

  // This test isn't actually testing the correct behaviour (see issue 2422:
  // https://github.com/wellcometrust/platform/issues/2422); it's due to be
  // replaced when we fix this behaviour.
  it("finds the ID if there is a window in progress") {
    withLocalS3Bucket { bucket =>
      withWindowManager(bucket) { windowManager =>
        val prefix =
          windowManager.buildWindowShard(s"[$startDateTime,$endDateTime]")

        // We pre-populate S3 with files as if they'd come from a prior run of the reader.
        s3Client.putObject(bucket.name, s"${prefix}0000.json", "[]")

        val record = createSierraBibRecordWith(
          id = SierraBibNumber("1794165")
        )

        s3Client.putObject(
          bucket.name,
          s"${prefix}0001.json",
          toJson(List(record)).get
        )

        val result =
          windowManager.getCurrentStatus(s"[$startDateTime,$endDateTime]")

        whenReady(result) { status =>
          status.id.get.withoutCheckDigit shouldBe "1794166"
          status.offset shouldBe 2
        }
      }
    }
  }

  it("throws an error if it finds invalid JSON in the bucket") {
    withLocalS3Bucket { bucket =>
      withWindowManager(bucket) { windowManager =>
        val prefix =
          windowManager.buildWindowShard(s"[$startDateTime,$endDateTime]")
        s3Client.putObject(bucket.name, s"${prefix}0000.json", "not valid")

        val result =
          windowManager.getCurrentStatus(s"[$startDateTime,$endDateTime]")

        whenReady(result.failed) {
          _ shouldBe a[SierraReaderException]
        }
      }
    }
  }

  it("throws an error if it finds empty JSON in the bucket") {
    withLocalS3Bucket { bucket =>
      withWindowManager(bucket) { windowManager =>
        val prefix =
          windowManager.buildWindowShard(s"[$startDateTime,$endDateTime]")

        s3Client.putObject(bucket.name, s"${prefix}0000.json", "[]")

        val result =
          windowManager.getCurrentStatus(s"[$startDateTime,$endDateTime]")

        whenReady(result.failed) {
          _ shouldBe a[SierraReaderException]
        }
      }
    }
  }

  it("throws an error if it finds a misnamed file in the bucket") {
    withLocalS3Bucket { bucket =>
      withWindowManager(bucket) { windowManager =>
        val prefix =
          windowManager.buildWindowShard(s"[$startDateTime,$endDateTime]")

        s3Client.putObject(bucket.name, s"${prefix}000x.json", "[]")

        val result =
          windowManager.getCurrentStatus(s"[$startDateTime,$endDateTime]")

        whenReady(result.failed) {
          _ shouldBe a[SierraReaderException]
        }
      }
    }
  }

  it("throws an error if it can't list the contents of the bucket") {
    withWindowManager(createBucket) { windowManager =>
      val future =
        windowManager.getCurrentStatus(s"[$startDateTime,$endDateTime]")

      whenReady(future.failed) {
        _ shouldBe an[AmazonS3Exception]
      }
    }
  }
}
