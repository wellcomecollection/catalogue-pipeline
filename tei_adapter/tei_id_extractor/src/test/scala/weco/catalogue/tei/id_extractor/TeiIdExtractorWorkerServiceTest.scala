package weco.catalogue.tei.id_extractor

import org.scalatest.concurrent.Eventually
import weco.catalogue.tei.id_extractor.fixtures.Wiremock
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.storage.fixtures.S3Fixtures
import uk.ac.wellcome.storage.s3.S3ObjectLocation

import java.time.ZonedDateTime

class TeiIdExtractorWorkerServiceTest extends AnyFunSpec with Wiremock with SQS with Akka with Eventually with S3Fixtures {
  it("receives a message, stores the file in s3 and send a message to the tei adapter with the file id"){
    withWiremock("localhost"){ port =>
      val repoUrl = s"http://localhost:$port"
      val message = {
        s"""
        {
          "path": "Arabic/WMS_Arabic_1.xml",
          "url": "$repoUrl/git/blobs/2e6b5fa45462510d5549b6bcf2bbc8b53ae08aed",
          "timeModified": "2021-05-27T14:05:00Z"
        }""".stripMargin
      }
      withLocalSqsQueuePair() { case QueuePair(queue, dlq) =>
        sendNotificationToSQS(queue, message)
        withActorSystem { implicit ac =>
          implicit val ec = ac.dispatcher
          withSQSStream(queue) { stream: SQSStream[NotificationMessage] =>
            withLocalS3Bucket { bucket =>
            val messageSender = new MemoryMessageSender()
              val gitHubBlobReader = new GitHubBlobReader()
            val service = new TeiIdExtractorWorkerService(messageStream = stream, messageSender = messageSender, gitHubBlobReader= gitHubBlobReader, concurrentFiles = 10)
            service.run()

            eventually{
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
              val keys = listKeysInBucket(bucket)
              keys should have size 1

              messageSender.getMessages[TeiIdChangeMessage]() should contain only(TeiIdChangeMessage(id="manuscript_15651", s3Location = S3ObjectLocation(bucket.name, keys.head), ZonedDateTime.now()))
            }
          }}}}
  }
}

  it("a message for a non TEI file is ignored"){
    fail()
  }

  it("handles file deleted messages"){
    fail()
  }

  it("handles a file being moved"){
    fail()
  }
}

case class TeiIdChangeMessage(id: String, s3Location: S3ObjectLocation, timeModified: ZonedDateTime)
