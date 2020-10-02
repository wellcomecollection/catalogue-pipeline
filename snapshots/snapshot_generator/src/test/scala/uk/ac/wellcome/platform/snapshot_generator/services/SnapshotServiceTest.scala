package uk.ac.wellcome.platform.snapshot_generator.services

import java.time.Instant

import akka.http.scaladsl.model.Uri
import akka.stream.alpakka.s3.S3Exception
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.http.JavaClientExceptionWrapper
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.display.json.DisplayJsonUtil
import uk.ac.wellcome.display.json.DisplayJsonUtil._
import uk.ac.wellcome.display.models.{ApiVersions, DisplayWork, WorksIncludes}
import uk.ac.wellcome.elasticsearch.ElasticClientBuilder
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.platform.snapshot_generator.fixtures.{
  AkkaS3,
  SnapshotServiceFixture
}
import uk.ac.wellcome.platform.snapshot_generator.models.{
  CompletedSnapshotJob,
  SnapshotJob
}
import uk.ac.wellcome.platform.snapshot_generator.test.utils.S3GzipUtils
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.s3.S3ObjectLocation

class SnapshotServiceTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with Akka
    with AkkaS3
    with S3GzipUtils
    with IntegrationPatience
    with SnapshotServiceFixture
    with WorkGenerators {

  def withFixtures[R](
    testWith: TestWith[(SnapshotService, Index, Bucket), R]): R =
    withActorSystem { implicit actorSystem =>
      withS3AkkaSettings { s3Settings =>
        withLocalWorksIndex { worksIndex =>
          withLocalS3Bucket { bucket =>
            withSnapshotService(s3Settings, worksIndex) { snapshotService =>
              testWith((snapshotService, worksIndex, bucket))
            }
          }
        }
      }
    }

  it("completes a snapshot generation") {
    withFixtures {
      case (snapshotService: SnapshotService, worksIndex, bucket) =>
        val visibleWorks = identifiedWorks(count = 3)
        val notVisibleWorks = identifiedWorks(count = 2).map { _.invisible() }

        val works = visibleWorks ++ notVisibleWorks

        insertIntoElasticsearch(worksIndex, works: _*)

        val s3Location = S3ObjectLocation(bucket.name, key = "target.txt.gz")
        val snapshotJob = SnapshotJob(
          s3Location = s3Location,
          apiVersion = ApiVersions.v2,
          requestedAt = Instant.now()
        )

        val future = snapshotService.generateSnapshot(snapshotJob)

        whenReady(future) { result =>
          val contents = getGzipObjectFromS3(s3Location)

          val expectedContents = visibleWorks
            .map {
              DisplayWork(_, includes = WorksIncludes.includeAll())
            }
            .map {
              DisplayJsonUtil.toJson(_)
            }
            .mkString("\n") + "\n"

          contents shouldBe expectedContents

          result shouldBe CompletedSnapshotJob(
            snapshotJob = snapshotJob,
            targetLocation =
              s"http://localhost:33333/${s3Location.bucket}/${s3Location.key}"
          )
        }
    }

  }

  it("completes a snapshot generation of an index with more than 10000 items") {
    withFixtures {
      case (snapshotService: SnapshotService, worksIndex, bucket) =>
        val works = identifiedWorks(count = 11000)
          .map { _.title(randomAlphanumeric(length = 1500)) }

        insertIntoElasticsearch(worksIndex, works: _*)

        val s3Location = S3ObjectLocation(bucket.name, key = "target.txt.gz")
        val snapshotJob = SnapshotJob(
          s3Location = s3Location,
          apiVersion = ApiVersions.v2,
          requestedAt = Instant.now()
        )

        val future = snapshotService.generateSnapshot(snapshotJob)

        whenReady(future) { result =>
          val contents = getGzipObjectFromS3(s3Location)

          val expectedContents = works
            .map {
              DisplayWork(_, includes = WorksIncludes.includeAll())
            }
            .map {
              DisplayJsonUtil.toJson(_)
            }
            .mkString("\n") + "\n"

          contents shouldBe expectedContents

          result shouldBe CompletedSnapshotJob(
            snapshotJob = snapshotJob,
            targetLocation =
              s"http://localhost:33333/${s3Location.bucket}/${s3Location.key}"
          )
        }
    }
  }

  it("returns a failed future if the S3 upload fails") {
    withFixtures {
      case (snapshotService: SnapshotService, worksIndex, _) =>
        val works = identifiedWorks(count = 3)

        insertIntoElasticsearch(worksIndex, works: _*)

        val snapshotJob = SnapshotJob(
          s3Location = createS3ObjectLocation,
          apiVersion = ApiVersions.v2,
          requestedAt = Instant.now
        )

        val future = snapshotService.generateSnapshot(snapshotJob)

        whenReady(future.failed) { result =>
          result shouldBe a[S3Exception]
        }
    }
  }

  it("returns a failed future if it fails reading from elasticsearch") {
    withActorSystem { implicit actorSystem =>
      withS3AkkaSettings { s3Settings =>
        val brokenElasticClient: ElasticClient = ElasticClientBuilder.create(
          hostname = "localhost",
          port = 8888,
          protocol = "http",
          username = "elastic",
          password = "changeme"
        )

        withSnapshotService(
          s3Settings,
          worksIndex = "wrong-index",
          elasticClient = brokenElasticClient) { brokenSnapshotService =>
          val snapshotJob = SnapshotJob(
            s3Location = createS3ObjectLocation,
            apiVersion = ApiVersions.v2,
            requestedAt = Instant.now()
          )

          val future = brokenSnapshotService.generateSnapshot(snapshotJob)

          whenReady(future.failed) { result =>
            result shouldBe a[JavaClientExceptionWrapper]
          }
        }
      }
    }
  }

  describe("buildLocation") {
    it("creates the correct object location in tests") {
      val location = createS3ObjectLocation

      withFixtures {
        case (snapshotService: SnapshotService, _, _) =>
          snapshotService.buildLocation(location) shouldBe Uri(
            s"http://localhost:33333/${location.bucket}/${location.key}")
      }
    }

    it("creates the correct object location with the default S3 endpoint") {
      val location = createS3ObjectLocation

      withActorSystem { implicit actorSystem =>
        withS3AkkaSettings(endpoint = "") { s3Settings =>
          withSnapshotService(s3Settings) {
            _.buildLocation(location) shouldBe Uri(
              s"s3://${location.bucket}/${location.key}")
          }
        }
      }
    }
  }
}
