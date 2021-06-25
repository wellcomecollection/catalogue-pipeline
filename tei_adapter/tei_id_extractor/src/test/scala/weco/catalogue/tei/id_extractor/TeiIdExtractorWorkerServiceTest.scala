package weco.catalogue.tei.id_extractor

import com.github.tomakehurst.wiremock.client.WireMock
import io.circe.Encoder
import org.apache.commons.io.IOUtils
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.akka.fixtures.Akka
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import weco.storage.fixtures.S3Fixtures.Bucket
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryStore
import weco.catalogue.tei.id_extractor.database.TableProvisioner
import weco.catalogue.tei.id_extractor.fixtures.{PathIdDatabase, Wiremock}
import com.github.tomakehurst.wiremock.client.WireMock
import io.circe.Encoder
import weco.fixtures.TestWith
import weco.storage.fixtures.S3Fixtures.Bucket
import weco.catalogue.tei.id_extractor.database.TableProvisioner
import weco.catalogue.source_model.tei.{
  TeiIdChangeMessage,
  TeiIdDeletedMessage,
  TeiIdMessage
}

import weco.http.client.AkkaHttpClient

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._
import scala.util.Try
import scala.xml.Utility.trim
import scala.xml.XML

class TeiIdExtractorWorkerServiceTest
    extends AnyFunSpec
    with Wiremock
    with SQS
    with Akka
    with Eventually
    with IntegrationPatience
    with PathIdDatabase {

  it(
    "receives a message, stores the file in s3 and send a message to the tei adapter with the file id") {
    withWorkerService() {
      case (QueuePair(queue, dlq), messageSender, store, bucket, repoUrl) =>
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

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          val expectedS3Location = checkFileIsStored(
            store,
            bucket,
            modifiedTime,
            IOUtils
              .resourceToString("/WMS_Arabic_1.xml", StandardCharsets.UTF_8))

          messageSender
            .getMessages[TeiIdChangeMessage]() should contain only (TeiIdChangeMessage(
            id = "manuscript_15651",
            s3Location = expectedS3Location,
            Instant.parse(modifiedTime)))

        }
    }
  }

  it("a message for a non TEI file is ignored") {
    withWorkerService() {
      case (QueuePair(queue, dlq), messageSender, store, bucket, repoUrl) =>
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
        eventually {
          WireMock.verify(
            WireMock.exactly(0),
            WireMock.getRequestedFor(
              WireMock.urlEqualTo(
                "/git/blobs/4bfe74311d86293447f173108190a4b4664d68ea")))
          store.entries.keySet shouldBe empty

          messageSender.getMessages[TeiIdChangeMessage]() shouldBe empty
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  it("an older message for a file changed is ignored") {
    withWorkerService() {
      case (QueuePair(queue, dlq), messageSender, store, bucket, repoUrl) =>
        val (createdTime: String, expectedS3Location: S3ObjectLocation) =
          createFile(queue, store, bucket, repoUrl)
        val modifiedTime = Instant.parse(createdTime).minus(2, ChronoUnit.HOURS)
        val message =
          s"""
          {
            "path": "Arabic/WMS_Arabic_1.xml",
            "uri": "$repoUrl/git/blobs/2e6b5fa45462510d5549b6bcf2bbc8b53ae08aed",
            "timeModified": "$modifiedTime"
          }""".stripMargin

        sendNotificationToSQS(queue, message)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          val changeMessage = TeiIdChangeMessage(
            id = "manuscript_15651",
            s3Location = expectedS3Location,
            Instant.parse(createdTime))
          val newKeyKey =
            s"tei_files/manuscript_15651/${modifiedTime.getEpochSecond}.xml"
          store.entries.keySet shouldNot contain(
            S3ObjectLocation(bucket.name, newKeyKey))
          messageSender
            .getMessages[TeiIdMessage]() should contain only (changeMessage)
        }

    }
  }

  it("handles file deleted messages") {
    withWorkerService() {
      case (QueuePair(queue, dlq), messageSender, store, bucket, repoUrl) =>
        val (modifiedTime: String, expectedS3Location: S3ObjectLocation) =
          createFile(queue, store, bucket, repoUrl)
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
          val changeMessage = TeiIdChangeMessage(
            id = "manuscript_15651",
            s3Location = expectedS3Location,
            Instant.parse(modifiedTime))
          val deletedMessage = TeiIdDeletedMessage(
            id = "manuscript_15651",
            Instant.parse(deletedTime))
          messageSender
            .getMessages[TeiIdMessage]() should contain only (changeMessage, deletedMessage)
        }

    }
  }

  it("if sending the file deleted message fails, the message can be retried") {
    val messageSender: MemoryMessageSender = new MemoryMessageSender {

      var attempts = 0
      override def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] = {
        if (attempts < 1 && t.isInstanceOf[TeiIdDeletedMessage]) {
          attempts += 1
          Try(throw new Exception("BOOOM!"))
        } else {
          super.sendT(t)
        }
      }
    }
    withWorkerService(messageSender) {
      case (QueuePair(queue, dlq), _, store, bucket, repoUrl) =>
        val (modifiedTime: String, expectedS3Location: S3ObjectLocation) =
          createFile(queue, store, bucket, repoUrl)
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
          val changeMessage = TeiIdChangeMessage(
            id = "manuscript_15651",
            s3Location = expectedS3Location,
            Instant.parse(modifiedTime))
          val deletedMessage = TeiIdDeletedMessage(
            id = "manuscript_15651",
            Instant.parse(deletedTime))
          messageSender
            .getMessages[TeiIdMessage]() should contain only (changeMessage, deletedMessage)
        }
    }
  }

  it("if sending the file changed message fails, the message can be retried") {
    val messageSender = new MemoryMessageSender {

      var attempts = 0
      override def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] = {
        if (attempts < 1) {
          attempts += 1
          Try(throw new Exception("BOOOM!"))
        } else {
          super.sendT(t)
        }
      }
    }
    withWorkerService(messageSender) {
      case (QueuePair(queue, dlq), _, store, bucket, repoUrl) =>
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

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          val expectedS3Location = checkFileIsStored(
            store,
            bucket,
            modifiedTime,
            IOUtils
              .resourceToString("/WMS_Arabic_1.xml", StandardCharsets.UTF_8))

          messageSender
            .getMessages[TeiIdChangeMessage]() should contain only (TeiIdChangeMessage(
            id = "manuscript_15651",
            s3Location = expectedS3Location,
            Instant.parse(modifiedTime)))

        }
    }
  }

  it("handles a file being moved") {
    withWorkerService() {
      case (QueuePair(queue, dlq), messageSender, store, bucket, repoUrl) =>
        val (createdTime: String, expectedS3Location: S3ObjectLocation) =
          createFile(queue, store, bucket, repoUrl)
        val movedTime = "2021-05-27T16:05:00Z"
        val fileMovedMessage =
          s"""
          {
            "path": "Arabic/another_path.xml",
            "uri": "$repoUrl/git/blobs/2e6b5fa45462510d5549b6bcf2bbc8b53ae08aed",
            "timeModified": "$movedTime"
          }""".stripMargin
        val messageDeleted =
          s"""
          {
            "path": "Arabic/WMS_Arabic_1.xml",
            "timeDeleted": "$movedTime"
          }""".stripMargin

        sendNotificationToSQS(queue, messageDeleted)
        sendNotificationToSQS(queue, fileMovedMessage)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          val expectedNewS3Location = checkFileIsStored(
            store,
            bucket,
            movedTime,
            IOUtils
              .resourceToString("/WMS_Arabic_1.xml", StandardCharsets.UTF_8))
          val fileCreatedMessage = TeiIdChangeMessage(
            id = "manuscript_15651",
            s3Location = expectedS3Location,
            Instant.parse(createdTime))
          val fileMovedMessage = TeiIdChangeMessage(
            id = "manuscript_15651",
            s3Location = expectedNewS3Location,
            Instant.parse(movedTime))
          messageSender
            .getMessages[TeiIdMessage]() should contain only (fileCreatedMessage, fileMovedMessage)
        }

    }
  }

  def withWorkerService[R](messageSender: MemoryMessageSender =
                             new MemoryMessageSender())(
    testWith: TestWith[(QueuePair,
                        MemoryMessageSender,
                        MemoryStore[S3ObjectLocation, String],
                        Bucket,
                        String),
                       R]): R =
    withWiremock("localhost") { port =>
      val repoUrl = s"http://localhost:$port"
      withLocalSqsQueuePair(3) {
        case q @ QueuePair(queue, dlq) =>
          withActorSystem { implicit ac =>
            implicit val ec = ac.dispatcher
            withSQSStream(queue) { stream: SQSStream[NotificationMessage] =>
              withPathIdTable {
                case (config, table) =>
                  val gitHubBlobReader = new GitHubBlobContentReader(
                    new AkkaHttpClient(),
                    "fake_token")
                  val store = new MemoryStore[S3ObjectLocation, String](Map())
                  val bucket = Bucket("bucket")
                  val service = new TeiIdExtractorWorkerService(
                    messageStream = stream,
                    tableProvisioner =
                      new TableProvisioner(rdsClientConfig, config),
                    gitHubBlobReader = gitHubBlobReader,
                    pathIdManager = new PathIdManager(
                      table,
                      store,
                      messageSender,
                      bucket.name),
                    config = TeiIdExtractorConfig(
                      parallelism = 10,
                      deleteMessageDelay = 500 milliseconds)
                  )
                  service.run()
                  testWith((q, messageSender, store, bucket, repoUrl))
              }
            }
          }
      }
    }

  private def createFile(queue: SQS.Queue,
                         store: MemoryStore[S3ObjectLocation, String],
                         bucket: Bucket,
                         repoUrl: String) = {
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

    val expectedS3Location = eventually {
      checkFileIsStored(
        store,
        bucket,
        modifiedTime,
        IOUtils.resourceToString("/WMS_Arabic_1.xml", StandardCharsets.UTF_8))
    }
    (modifiedTime, expectedS3Location)
  }

  private def checkFileIsStored(store: MemoryStore[S3ObjectLocation, String],
                                bucket: Bucket,
                                modifiedTime: String,
                                fileContents: String) = {
    val expectedKey =
      s"tei_files/manuscript_15651/${Instant.parse(modifiedTime).getEpochSecond}.xml"
    val expectedS3Location = S3ObjectLocation(bucket.name, expectedKey)
    store.entries.keySet should contain(expectedS3Location)
    trim(XML.loadString(store.entries(expectedS3Location))) shouldBe trim(
      XML.loadString(fileContents))
    expectedS3Location
  }
}
