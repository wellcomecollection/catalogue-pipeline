package weco.catalogue.tei.id_extractor

import org.apache.commons.io.IOUtils
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.memory.MemoryStore
import weco.catalogue.tei.id_extractor.fixtures.{PathIdDatabase, Wiremock}
import com.github.tomakehurst.wiremock.client.WireMock
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import weco.catalogue.tei.id_extractor.models.{TeiIdChangeMessage, TeiIdDeletedMessage, TeiIdMessage}

import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime

class TeiIdExtractorWorkerServiceTest extends AnyFunSpec with Wiremock with SQS with Akka with Eventually with IntegrationPatience with PathIdDatabase{


  it("receives a message, stores the file in s3 and send a message to the tei adapter with the file id"){
    withWorkerService{ case (QueuePair(queue, dlq), messageSender, store, bucket, repoUrl) =>
      val modifiedTime = "2021-05-27T14:05:00Z"
      val message = {
        s"""
        {
          "path": "Arabic/WMS_Arabic_1.xml",
          "uri": "$repoUrl/git/blobs/2e6b5fa45462510d5549b6bcf2bbc8b53ae08aed",
          "timeModified": "$modifiedTime"
        }""".stripMargin
      }
        sendNotificationToSQS(queue, message)

            eventually{
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
              val expectedS3Location = checkFileIsStored(store, bucket, modifiedTime,IOUtils.resourceToString("/WMS_Arabic_1.xml", StandardCharsets.UTF_8))

              messageSender.getMessages[TeiIdChangeMessage]() should contain only(TeiIdChangeMessage(id="manuscript_15651", s3Location = expectedS3Location, ZonedDateTime.parse(modifiedTime)))

            }
          }}

  it("a message for a non TEI file is ignored"){
    withWorkerService{ case (QueuePair(queue, dlq), messageSender, store, bucket, repoUrl) =>
      val modifiedTime = "2021-05-27T14:05:00Z"
      val message = {
        s"""
        {
          "path": "Arabic/README.md",
          "uri": "$repoUrl/git/blobs/4bfe74311d86293447f173108190a4b4664d68ea",
          "timeModified": "$modifiedTime"
        }""".stripMargin
      }

        sendNotificationToSQS(queue, message)
            Thread.sleep(200)
            eventually{
              WireMock.verify(WireMock.exactly(0), WireMock.getRequestedFor(WireMock.urlEqualTo("/git/blobs/4bfe74311d86293447f173108190a4b4664d68ea")))
              store.entries.keySet shouldBe empty

              messageSender.getMessages[TeiIdChangeMessage]() shouldBe empty
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }
          }}

  it("handles file deleted messages"){
    withWorkerService { case (QueuePair(queue, dlq), messageSender, store, bucket, repoUrl) =>

      val modifiedTime = "2021-05-27T14:05:00Z"
      val message = {
        s"""
        {
          "path": "Arabic/WMS_Arabic_1.xml",
          "uri": "$repoUrl/git/blobs/2e6b5fa45462510d5549b6bcf2bbc8b53ae08aed",
          "timeModified": "$modifiedTime"
        }""".stripMargin
      }
      sendNotificationToSQS(queue, message)

      eventually{
        val expectedS3Location = checkFileIsStored(store, bucket, modifiedTime,IOUtils.resourceToString("/WMS_Arabic_1.xml", StandardCharsets.UTF_8))

        val deletedTime = "2021-05-27T16:05:00Z"
        val messageDeleted =
          s"""
          {
            "path": "Arabic/WMS_Arabic_1.xml",
            "timeDeleted": "$deletedTime"
          }""".stripMargin

        sendNotificationToSQS(queue, messageDeleted)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          val changeMessage = TeiIdChangeMessage(id = "manuscript_15651", s3Location = expectedS3Location, ZonedDateTime.parse(modifiedTime))
          val deletedMessage = TeiIdDeletedMessage(id = "manuscript_15651", ZonedDateTime.parse(deletedTime))
          messageSender.getMessages[TeiIdMessage]() should contain only(changeMessage, deletedMessage)
        }
      }


    }
  }

  it("handles a file being moved"){
    fail()
  }


  def withWorkerService[R](testWith: TestWith[(QueuePair, MemoryMessageSender, MemoryStore[S3ObjectLocation, String], Bucket, String), R]): R =
    withWiremock("localhost"){ port =>
      val repoUrl = s"http://localhost:$port"
    withLocalSqsQueuePair(10) { case q@QueuePair(queue, dlq) =>
      withActorSystem { implicit ac =>
        implicit val ec = ac.dispatcher
        withSQSStream(queue) { stream: SQSStream[NotificationMessage] =>
          withPathIdDao(initializeTable = false) { case (provisioner,_, pathIdDao) =>

            val messageSender = new MemoryMessageSender()
            val gitHubBlobReader = new GitHubBlobReader()
            val store = new MemoryStore[S3ObjectLocation, String](Map())
            val bucket = Bucket("bucket")
            val service = new TeiIdExtractorWorkerService(
              messageStream = stream,
              messageSender = messageSender,
              tableProvisioner = provisioner,
              gitHubBlobReader = gitHubBlobReader,
              idExtractor = new IdExtractor,
              store = store,
              pathIdDao = pathIdDao,
              config = TeiIdExtractorConfig(concurrentFiles = 10, bucket = bucket.name))
            service.run()
            testWith((q, messageSender, store, bucket, repoUrl))
          }
        }}}
  }
  private def checkFileIsStored(store: MemoryStore[S3ObjectLocation, String], bucket: Bucket, modifiedTime: String, fileContents: String) = {
    val expectedKey = s"tei_files/manuscript_15651/${ZonedDateTime.parse(modifiedTime).toEpochSecond}.xml"
    val expectedS3Location = S3ObjectLocation(bucket.name, expectedKey)
    store.entries.keySet should contain only (expectedS3Location)
    store.entries(expectedS3Location) shouldBe fileContents
    expectedS3Location
  }
}


