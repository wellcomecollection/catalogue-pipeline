package weco.pipeline.sierra_reader.services

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
import weco.storage.s3.S3ObjectLocation

class WindowManagerTest
    extends AnyFunSpec
    with Matchers
    with S3Fixtures
    with ScalaFutures
    with IntegrationPatience
    with SierraRecordGenerators {

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
        putString(
          location = S3ObjectLocation(bucket.name, key = s"${prefix}0000.json"),
          contents = "[]"
        )

        val record = createSierraBibRecordWith(
          id = SierraBibNumber("1794165")
        )

        putString(
          location = S3ObjectLocation(bucket.name, key=
          s"${prefix}0001.json"),
          contents = toJson(List(record)).get
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
        putString(
          location = S3ObjectLocation(bucket.name, key = s"${prefix}0000.json"),
          contents = "not valid"
        )

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

        putString(
          location = S3ObjectLocation(bucket.name, key = s"${prefix}0000.json"),
          contents = "[]"
        )

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

        putString(
          location = S3ObjectLocation(bucket.name, key = s"${prefix}000x.json"),
          contents = "[]"
        )

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
