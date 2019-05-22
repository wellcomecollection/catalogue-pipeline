package uk.ac.wellcome.platform.goobi_reader

import java.time.Instant

import org.scalatest.concurrent.Eventually
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.platform.goobi_reader.fixtures.GoobiReaderFixtures
import uk.ac.wellcome.platform.goobi_reader.models.GoobiRecordMetadata
import uk.ac.wellcome.platform.goobi_reader.services.GoobiReaderWorkerService
import uk.ac.wellcome.storage.fixtures.S3
import uk.ac.wellcome.storage.fixtures.S3.Bucket
import uk.ac.wellcome.storage.memory.MemoryVersionedDao
import uk.ac.wellcome.storage.vhs.Entry

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

class GoobiReaderFeatureTest
    extends FunSpec
    with Eventually
    with Matchers
    with GoobiReaderFixtures
    with SQS
    with S3 {

  private val eventTime = Instant.now()

  it("gets an S3 notification and puts the new record in VHS") {
    withLocalS3Bucket { bucket =>
      withLocalSqsQueue { queue =>
        val id = "mets-0001"
        val sourceKey = s"$id.xml"
        val contents = "muddling the machinations of morose METS"

        s3Client.putObject(bucket.name, sourceKey, contents)

        sendNotificationToSQS(
          queue = queue,
          body = createS3Notification(sourceKey, bucket.name, eventTime)
        )

        val dao = MemoryVersionedDao[String, Entry[String, GoobiRecordMetadata]]

        withWorkerService(queue, bucket, dao) { _ =>
          eventually {
            val storedEntry = dao.get(id)
            storedEntry shouldBe a[Success[_]]

            storedEntry.get.get.id shouldBe id
            storedEntry.get.get.version shouldBe 1

            val location = storedEntry.get.get.location
            val s3contents = getContentFromS3(location)
            s3contents shouldBe contents
          }
        }
      }
    }
  }

  private def withWorkerService[R](queue: Queue, bucket: Bucket, dao: MemoryVersionedDao[String, Entry[String, GoobiRecordMetadata]])(
    testWith: TestWith[GoobiReaderWorkerService, R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val workerService = new GoobiReaderWorkerService(
          s3Client = s3Client,
          sqsStream = sqsStream,
          vhs = createVHS(bucket, dao)
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
