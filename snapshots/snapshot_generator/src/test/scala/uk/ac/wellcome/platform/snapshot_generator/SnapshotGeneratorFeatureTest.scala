package uk.ac.wellcome.platform.snapshot_generator

import java.time.Instant

import com.sksamuel.elastic4s.Index
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.display.models.{ApiVersions, DisplaySerialisationTestBase}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.Work
import uk.ac.wellcome.platform.snapshot_generator.fixtures.WorkerServiceFixture
import uk.ac.wellcome.platform.snapshot_generator.models.{
  CompletedSnapshotJob,
  SnapshotJob
}
import uk.ac.wellcome.platform.snapshot_generator.test.utils.S3GzipUtils
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.s3.S3ObjectLocation

class SnapshotGeneratorFeatureTest
    extends AnyFunSpec
    with Eventually
    with Matchers
    with Akka
    with S3GzipUtils
    with JsonAssertions
    with IntegrationPatience
    with DisplaySerialisationTestBase
    with WorkerServiceFixture
    with WorkGenerators {

  it("completes a snapshot generation") {
    withFixtures {
      case (queue, messageSender, worksIndex, _, bucket) =>
        val works = identifiedWorks(count = 3)

        insertIntoElasticsearch(worksIndex, works: _*)

        val s3Location = S3ObjectLocation(bucket.name, key = "target.tar.gz")

        val snapshotJob = SnapshotJob(
          s3Location = s3Location,
          requestedAt = Instant.now(),
          apiVersion = ApiVersions.v2
        )

        sendNotificationToSQS(queue = queue, message = snapshotJob)

        eventually {

          val (objectMetadata, contents) = getGzipObjectFromS3(s3Location)

          val actualJsonLines = contents.split("\n").toList

          val s3Etag = objectMetadata.getETag
          val s3Size = objectMetadata.getContentLength

          val expectedJsonLines = works.sortBy { _.state.canonicalId }.map {
            work =>
              s"""
              |{
              |  "id": "${work.state.canonicalId}",
              |  "title": "${work.data.title.get}",
              |  "identifiers": [ ${identifier(work.sourceIdentifier)} ],
              |  "contributors": [ ],
              |  "genres": [ ],
              |  "subjects": [ ],
              |  "items": [ ],
              |  "production": [ ],
              |  "alternativeTitles": [ ],
              |  "notes": [ ],
              |  "images": [ ],
              |  "type": "Work"
              |}""".stripMargin
          }

          actualJsonLines.sorted.zip(expectedJsonLines).foreach {
            case (actualLine, expectedLine) =>
              println(s"actualLine = <<$actualLine>>")
              assertJsonStringsAreEqual(actualLine, expectedLine)
          }

          val result = messageSender.getMessages[CompletedSnapshotJob].head

          result.snapshotJob shouldBe snapshotJob

          result.snapshotResult.indexName shouldBe worksIndex.name
          result.snapshotResult.documentCount shouldBe works.length
          result.snapshotResult.displayModel shouldBe Work.getClass.getCanonicalName

          result.snapshotResult.startedAt shouldBe >(
            result.snapshotJob.requestedAt)
          result.snapshotResult.finishedAt shouldBe >(
            result.snapshotResult.startedAt)

          result.snapshotResult.s3Etag shouldBe s3Etag
          result.snapshotResult.s3Size shouldBe s3Size
          result.snapshotResult.s3Location shouldBe s3Location
        }
    }
  }

  def withFixtures[R](
    testWith: TestWith[(Queue, MemoryMessageSender, Index, Index, Bucket), R])
    : R =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueue(visibilityTimeout = 5) { queue =>
        val messageSender = new MemoryMessageSender()

        withLocalWorksIndex { worksIndex =>
          withLocalS3Bucket { bucket =>
            withWorkerService(queue, messageSender, worksIndex) { _ =>
              testWith((queue, messageSender, worksIndex, worksIndex, bucket))
            }
          }
        }
      }
    }
}
