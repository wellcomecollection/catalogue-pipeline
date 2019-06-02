package uk.ac.wellcome.platform.snapshot_generator

import java.io.File

import com.amazonaws.services.s3.model.GetObjectRequest
import com.sksamuel.elastic4s.Index
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.display.models.ApiVersions
import uk.ac.wellcome.display.models.v1.DisplayV1SerialisationTestBase
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.platform.snapshot_generator.fixtures.WorkerServiceFixture
import uk.ac.wellcome.platform.snapshot_generator.models.{
  CompletedSnapshotJob,
  SnapshotJob
}
import uk.ac.wellcome.platform.snapshot_generator.test.utils.GzipUtils
import uk.ac.wellcome.storage.fixtures.S3.Bucket

class SnapshotGeneratorFeatureTest
    extends FunSpec
    with Eventually
    with Matchers
    with GzipUtils
    with JsonAssertions
    with IntegrationPatience
    with DisplayV1SerialisationTestBase
    with WorkerServiceFixture
    with WorksGenerators {

  it("completes a snapshot generation") {
    withFixtures {
      case (queue, messageSender, indexV1, _, publicBucket: Bucket) =>
        val works = createIdentifiedWorks(count = 3)

        insertIntoElasticsearch(indexV1, works: _*)

        val publicObjectKey = "target.txt.gz"

        val snapshotJob = SnapshotJob(
          publicBucketName = publicBucket.name,
          publicObjectKey = publicObjectKey,
          apiVersion = ApiVersions.v1
        )

        sendNotificationToSQS(queue = queue, message = snapshotJob)

        eventually {
          val downloadFile =
            File.createTempFile("snapshotGeneratorFeatureTest", ".txt.gz")

          s3Client.getObject(
            new GetObjectRequest(publicBucket.name, publicObjectKey),
            downloadFile)

          val actualJsonLines: List[String] =
            readGzipFile(downloadFile.getPath).split("\n").toList

          val expectedJsonLines = works.sortBy { _.canonicalId }.map { work =>
            s"""{
                 |  "id": "${work.canonicalId}",
                 |  "title": "${work.title}",
                 |  "identifiers": [ ${identifier(work.sourceIdentifier)} ],
                 |  "creators": [ ],
                 |  "genres": [ ],
                 |  "subjects": [ ],
                 |  "items": [ ],
                 |  "publishers": [ ],
                 |  "placesOfPublication": [ ],
                 |  "type": "Work"
                   }""".stripMargin
          }

          actualJsonLines.sorted.zip(expectedJsonLines).foreach {
            case (actualLine, expectedLine) =>
              println(s"actualLine = <<$actualLine>>")
              assertJsonStringsAreEqual(actualLine, expectedLine)
          }

          val receivedMessages = messageSender.getMessages[CompletedSnapshotJob]

          val expectedJob = CompletedSnapshotJob(
            snapshotJob = snapshotJob,
            targetLocation =
              s"http://localhost:33333/${publicBucket.name}/$publicObjectKey"
          )

          receivedMessages shouldBe Seq(expectedJob)
        }
    }

  }

  def withFixtures[R](
    testWith: TestWith[(Queue, MemoryMessageSender, Index, Index, Bucket), R]) =
    withActorSystem { implicit actorSystem =>
      withMaterializer(actorSystem) { implicit materializer =>
        withLocalSqsQueue { queue =>
          val messageSender = new MemoryMessageSender()
          withLocalWorksIndex { indexV1 =>
            withLocalWorksIndex { indexV2 =>
              withLocalS3Bucket { bucket =>
                withWorkerService(queue, messageSender, indexV1, indexV2) { _ =>
                  testWith((queue, messageSender, indexV1, indexV2, bucket))
                }
              }
            }
          }
        }
      }
    }
}
