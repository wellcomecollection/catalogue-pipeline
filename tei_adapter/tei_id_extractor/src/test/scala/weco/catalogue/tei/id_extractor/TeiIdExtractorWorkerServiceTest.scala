package weco.catalogue.tei.id_extractor

import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpRequest,
  HttpResponse,
  StatusCodes
}
import io.circe.Encoder
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import weco.pekko.fixtures.Pekko
import weco.catalogue.source_model.tei.{
  TeiIdChangeMessage,
  TeiIdDeletedMessage,
  TeiIdMessage
}
import weco.catalogue.source_model.Implicits._
import weco.catalogue.tei.id_extractor.database.TableProvisioner
import weco.catalogue.tei.id_extractor.fixtures.{PathIdDatabase, XmlAssertions}
import weco.fixtures.{LocalResources, TestWith}
import weco.http.client.HttpClient
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.storage.fixtures.S3Fixtures.Bucket
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryStore

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class TeiIdExtractorWorkerServiceTest
    extends AnyFunSpec
    with SQS
    with Pekko
    with Eventually
    with IntegrationPatience
    with PathIdDatabase
    with LocalResources
    with XmlAssertions {

  val repoUrl = "http://github:1234"

  it(
    "receives a message, stores the file in s3 and send a message to the tei adapter with the file id"
  ) {
    withWorkerService() {
      case (QueuePair(queue, dlq), messageSender, store, bucket) =>
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
            filename = "WMS_Arabic_1.xml"
          )

          messageSender
            .getMessages[
              TeiIdChangeMessage
            ]() should contain only TeiIdChangeMessage(
            id = "manuscript_15651",
            s3Location = expectedS3Location,
            Instant.parse(modifiedTime)
          )

        }
    }
  }

  it("a message for a non TEI file is ignored") {
    val neverCallClient = new HttpClient {
      override def singleRequest(request: HttpRequest): Future[HttpResponse] =
        Future.failed(new Throwable("This should never be called!"))
    }

    withWorkerService(httpClient = neverCallClient) {
      case (QueuePair(queue, dlq), messageSender, store, _) =>
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

        eventually {
          store.entries.keySet shouldBe empty

          messageSender.messages shouldBe empty

          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  it("an older message for a file changed is ignored") {
    withWorkerService() {
      case (QueuePair(queue, dlq), messageSender, store, bucket) =>
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
            Instant.parse(createdTime)
          )
          val newKeyKey =
            s"tei_files/manuscript_15651/${modifiedTime.getEpochSecond}.xml"
          store.entries.keySet shouldNot contain(
            S3ObjectLocation(bucket.name, newKeyKey)
          )
          messageSender
            .getMessages[TeiIdMessage]() should contain only changeMessage
        }

    }
  }

  it("handles file deleted messages") {
    withWorkerService() {
      case (QueuePair(queue, dlq), messageSender, store, bucket) =>
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
            Instant.parse(modifiedTime)
          )
          val deletedMessage = TeiIdDeletedMessage(
            id = "manuscript_15651",
            Instant.parse(deletedTime)
          )
          messageSender
            .getMessages[
              TeiIdMessage
            ]() should contain only (changeMessage, deletedMessage)
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
      case (QueuePair(queue, dlq), _, store, bucket) =>
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
            Instant.parse(modifiedTime)
          )
          val deletedMessage = TeiIdDeletedMessage(
            id = "manuscript_15651",
            Instant.parse(deletedTime)
          )
          messageSender
            .getMessages[
              TeiIdMessage
            ]() should contain only (changeMessage, deletedMessage)
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
      case (QueuePair(queue, dlq), _, store, bucket) =>
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
            filename = "WMS_Arabic_1.xml"
          )

          messageSender
            .getMessages[
              TeiIdChangeMessage
            ]() should contain only TeiIdChangeMessage(
            id = "manuscript_15651",
            s3Location = expectedS3Location,
            Instant.parse(modifiedTime)
          )

        }
    }
  }

  it("handles a file being moved") {
    withWorkerService() {
      case (QueuePair(queue, dlq), messageSender, store, bucket) =>
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
            filename = "WMS_Arabic_1.xml"
          )

          val fileCreatedMessage = TeiIdChangeMessage(
            id = "manuscript_15651",
            s3Location = expectedS3Location,
            Instant.parse(createdTime)
          )
          val fileMovedMessage = TeiIdChangeMessage(
            id = "manuscript_15651",
            s3Location = expectedNewS3Location,
            Instant.parse(movedTime)
          )
          messageSender
            .getMessages[
              TeiIdMessage
            ]() should contain only (fileCreatedMessage, fileMovedMessage)
        }

    }
  }

  private val httpClient = new HttpClient {
    override def singleRequest(request: HttpRequest): Future[HttpResponse] =
      request.uri.path match {
        case Path("/git/blobs/2e6b5fa45462510d5549b6bcf2bbc8b53ae08aed") =>
          Future.successful(
            HttpResponse(
              entity = HttpEntity(
                contentType = ContentTypes.`application/json`,
                readResource("github-blob-2e6b5fa.json")
              )
            )
          )

        case Path("/git/blobs/ddffeb761e5158b41a3780cda22346978d2cd6bd") =>
          Future.successful(
            HttpResponse(
              entity = HttpEntity(
                contentType = ContentTypes.`application/json`,
                readResource("github-blob-ddffeb7.json")
              )
            )
          )

        case _ =>
          Future.successful(HttpResponse(status = StatusCodes.NotFound))
      }
  }

  def withWorkerService[R](
    messageSender: MemoryMessageSender = new MemoryMessageSender(),
    httpClient: HttpClient = httpClient
  )(
    testWith: TestWith[
      (
        QueuePair,
        MemoryMessageSender,
        MemoryStore[S3ObjectLocation, String],
        Bucket
      ),
      R
    ]
  ): R =
    withLocalSqsQueuePair(visibilityTimeout = 3.seconds) {
      case q @ QueuePair(queue, _) =>
        withActorSystem {
          implicit ac =>
            implicit val ec = ac.dispatcher
            withSQSStream(queue) {
              stream: SQSStream[NotificationMessage] =>
                withPathIdTable {
                  case (config, table) =>
                    val gitHubBlobReader =
                      new GitHubBlobContentReader(httpClient)

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
                        bucket.name
                      ),
                      config = TeiIdExtractorConfig(
                        parallelism = 10,
                        deleteMessageDelay = 500 milliseconds
                      )
                    )
                    service.run()
                    testWith((q, messageSender, store, bucket))
                }
            }
        }
    }

  private def createFile(
    queue: SQS.Queue,
    store: MemoryStore[S3ObjectLocation, String],
    bucket: Bucket,
    repoUrl: String
  ) = {
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
        filename = "WMS_Arabic_1.xml"
      )
    }
    (modifiedTime, expectedS3Location)
  }

  private def checkFileIsStored(
    store: MemoryStore[S3ObjectLocation, String],
    bucket: Bucket,
    modifiedTime: String,
    filename: String
  ) = {
    val expectedKey =
      s"tei_files/manuscript_15651/${Instant.parse(modifiedTime).getEpochSecond}.xml"
    val expectedS3Location = S3ObjectLocation(bucket.name, expectedKey)
    store.entries.keySet should contain(expectedS3Location)

    assertXmlStringsAreEqual(
      store.entries(expectedS3Location),
      readResource(filename)
    )

    expectedS3Location
  }
}
