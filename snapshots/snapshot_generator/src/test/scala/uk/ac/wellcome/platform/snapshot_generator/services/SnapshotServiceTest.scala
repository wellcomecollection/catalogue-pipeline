package uk.ac.wellcome.platform.snapshot_generator.services

import java.io.File

import akka.http.scaladsl.model.Uri
import akka.stream.alpakka.s3.S3Exception
import com.amazonaws.services.s3.model.GetObjectRequest
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
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.platform.snapshot_generator.fixtures.{
  AkkaS3,
  SnapshotServiceFixture
}
import uk.ac.wellcome.platform.snapshot_generator.models.{
  CompletedSnapshotJob,
  SnapshotJob
}
import uk.ac.wellcome.platform.snapshot_generator.test.utils.GzipUtils
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket

class SnapshotServiceTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with Akka
    with AkkaS3
    with S3Fixtures
    with GzipUtils
    with IntegrationPatience
    with SnapshotServiceFixture
    with WorksGenerators {

  def withFixtures[R](
    testWith: TestWith[(SnapshotService, Index, Bucket), R]): R =
    withActorSystem { implicit actorSystem =>
      withMaterializer(actorSystem) { implicit materializer =>
        withS3AkkaSettings { s3Settings =>
          withLocalWorksIndex { worksIndex =>
            withLocalS3Bucket { bucket =>
              withSnapshotService(s3Settings, worksIndex) { snapshotService =>
                {
                  testWith((snapshotService, worksIndex, bucket))
                }
              }
            }
          }
        }
      }
    }

  it("completes a snapshot generation") {
    withFixtures {
      case (snapshotService: SnapshotService, worksIndex, publicBucket) =>
        val visibleWorks = createIdentifiedWorks(count = 4)
        val notVisibleWorks = createIdentifiedInvisibleWorks(count = 2)

        val works = visibleWorks ++ notVisibleWorks

        insertIntoElasticsearch(worksIndex, works: _*)

        val publicObjectKey = "target.txt.gz"

        val snapshotJob = SnapshotJob(
          publicBucketName = publicBucket.name,
          publicObjectKey = publicObjectKey,
          apiVersion = ApiVersions.v2
        )

        val future = snapshotService.generateSnapshot(snapshotJob)

        whenReady(future) { result =>
          val downloadFile =
            File.createTempFile("snapshotServiceTest", ".txt.gz")
          s3Client.getObject(
            new GetObjectRequest(publicBucket.name, publicObjectKey),
            downloadFile)

          val contents = readGzipFile(downloadFile.getPath)
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
              s"http://localhost:33333/${publicBucket.name}/$publicObjectKey"
          )
        }
    }

  }

  it("completes a snapshot generation of an index with more than 10000 items") {
    withFixtures {
      case (snapshotService: SnapshotService, worksIndex, publicBucket) =>
        val works = (1 to 11000).map { id =>
          createIdentifiedWorkWith(
            title = Some(randomAlphanumeric(length = 1500))
          )
        }

        insertIntoElasticsearch(worksIndex, works: _*)

        val publicObjectKey = "target.txt.gz"
        val snapshotJob = SnapshotJob(
          publicBucketName = publicBucket.name,
          publicObjectKey = publicObjectKey,
          apiVersion = ApiVersions.v2
        )

        val future = snapshotService.generateSnapshot(snapshotJob)

        whenReady(future) { result =>
          val downloadFile =
            File.createTempFile("snapshotServiceTest", ".txt.gz")
          s3Client.getObject(
            new GetObjectRequest(publicBucket.name, publicObjectKey),
            downloadFile)

          val contents = readGzipFile(downloadFile.getPath)
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
              s"http://localhost:33333/${publicBucket.name}/$publicObjectKey"
          )
        }
    }
  }

  it("returns a failed future if the S3 upload fails") {
    withFixtures {
      case (snapshotService: SnapshotService, worksIndex, _) =>
        val works = createIdentifiedWorks(count = 3)

        insertIntoElasticsearch(worksIndex, works: _*)

        val snapshotJob = SnapshotJob(
          publicBucketName = "wrongBukkit",
          publicObjectKey = "target.json.gz",
          apiVersion = ApiVersions.v2
        )

        val future = snapshotService.generateSnapshot(snapshotJob)

        whenReady(future.failed) { result =>
          result shouldBe a[S3Exception]
        }
    }
  }

  it("returns a failed future if it fails reading from elasticsearch") {
    withActorSystem { implicit actorSystem =>
      withMaterializer(actorSystem) { implicit materializer =>
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
              publicBucketName = "bukkit",
              publicObjectKey = "target.json.gz",
              apiVersion = ApiVersions.v2
            )

            val future = brokenSnapshotService.generateSnapshot(snapshotJob)

            whenReady(future.failed) { result =>
              result shouldBe a[JavaClientExceptionWrapper]
            }
          }
        }
      }
    }
  }

  describe("buildLocation") {
    it("creates the correct object location in tests") {
      withFixtures {
        case (snapshotService: SnapshotService, _, _) =>
          snapshotService.buildLocation(
            bucketName = "bukkit",
            objectKey = "snapshot.json.gz"
          ) shouldBe Uri("http://localhost:33333/bukkit/snapshot.json.gz")
      }
    }

    it("creates the correct object location with the default S3 endpoint") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          withS3AkkaSettings(endpoint = "") { s3Settings =>
            withSnapshotService(s3Settings, worksIndex = "worksIndex") {
              snapshotService =>
                snapshotService.buildLocation(
                  bucketName = "bukkit",
                  objectKey = "snapshot.json.gz"
                ) shouldBe Uri("s3://bukkit/snapshot.json.gz")
            }
          }
        }
      }
    }
  }
}
