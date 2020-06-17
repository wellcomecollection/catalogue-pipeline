package uk.ac.wellcome.platform.sierra_reader.services

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.sierra_reader.config.models.ReaderConfig
import uk.ac.wellcome.platform.sierra_reader.exceptions.SierraReaderException
import uk.ac.wellcome.platform.sierra_reader.models.{
  SierraResourceTypes,
  WindowStatus
}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.sierra_adapter.model.{SierraBibNumber, SierraGenerators}
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket

import scala.concurrent.ExecutionContext.Implicits.global

class WindowManagerTest
    extends AnyFunSpec
    with Matchers
    with S3Fixtures
    with ScalaFutures
    with IntegrationPatience
    with SierraGenerators {

  private def withWindowManager[R](bucket: Bucket)(
    testWith: TestWith[WindowManager, R]) = {
    val windowManager = new WindowManager(
      s3client = s3Client,
      s3Config = createS3ConfigWith(bucket),
      readerConfig = ReaderConfig(
        resourceType = SierraResourceTypes.bibs,
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
        s3Client.putObject(bucket.name, s"$prefix/0000.json", "[]")

        val record = createSierraBibRecordWith(
          id = SierraBibNumber("1794165")
        )

        s3Client.putObject(
          bucket.name,
          s"$prefix/0001.json",
          toJson(List(record)).get
        )

        val result =
          windowManager.getCurrentStatus(s"[$startDateTime,$endDateTime]")

        whenReady(result) {
          _ shouldBe WindowStatus(id = "1794166", offset = 2)
        }
      }
    }
  }

  it("throws an error if it finds invalid JSON in the bucket") {
    withLocalS3Bucket { bucket =>
      withWindowManager(bucket) { windowManager =>
        val prefix =
          windowManager.buildWindowShard(s"[$startDateTime,$endDateTime]")
        s3Client.putObject(bucket.name, s"$prefix/0000.json", "not valid")

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

        s3Client.putObject(bucket.name, s"$prefix/0000.json", "[]")

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

        s3Client.putObject(bucket.name, s"$prefix/000x.json", "[]")

        val result =
          windowManager.getCurrentStatus(s"[$startDateTime,$endDateTime]")

        whenReady(result.failed) {
          _ shouldBe a[SierraReaderException]
        }
      }
    }
  }
}
