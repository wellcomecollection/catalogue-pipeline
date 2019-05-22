package uk.ac.wellcome.platform.goobi_reader.services

import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.temporal.ChronoUnit

import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import org.mockito.Matchers.{any, endsWith}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Assertion, FunSpec, Inside}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.Messaging
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import uk.ac.wellcome.platform.goobi_reader.fixtures.GoobiReaderFixtures
import uk.ac.wellcome.platform.goobi_reader.models.GoobiRecordMetadata
import uk.ac.wellcome.storage.fixtures.S3.Bucket
import uk.ac.wellcome.storage.memory.MemoryVersionedDao
import uk.ac.wellcome.storage.vhs.Entry

import scala.concurrent.ExecutionContext.Implicits.global

class GoobiReaderWorkerServiceTest
    extends FunSpec
    with MetricsSenderFixture
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar
    with GoobiReaderFixtures
    with Messaging
    with Inside {

  private val id = "mets-0001"
  private val sourceKey = s"$id.xml"
  private val contents = "muddling the machinations of morose METS"
  private val updatedContents = "Updated contents"
  private val eventTime = Instant.parse("2018-01-01T01:00:00.000Z")

  it("processes a notification") {
    withGoobiReaderWorkerService(s3Client) {
      case (bucket, QueuePair(queue, _), _, dao, _) =>
        s3Client.putObject(bucket.name, sourceKey, contents)

        sendNotificationToSQS(
          queue = queue,
          body = createS3Notification(sourceKey, bucket.name, eventTime)
        )

        eventually {
          assertRecordStored(
            id = id,
            version = 1,
            expectedMetadata = GoobiRecordMetadata(eventTime),
            expectedContents = contents,
            dao = dao,
            bucket = bucket)
        }
    }
  }

  it("ingests an object with a space in the s3Key") {
    withGoobiReaderWorkerService(s3Client) {
      case (bucket, QueuePair(queue, _), _, dao, _) =>
        val sourceKey = s"$id work.xml"
        val urlEncodedSourceKey =
          java.net.URLEncoder.encode(sourceKey, "utf-8")
        s3Client.putObject(bucket.name, sourceKey, contents)

        sendNotificationToSQS(
          queue = queue,
          body = createS3Notification(urlEncodedSourceKey, bucket.name, eventTime)
        )

        eventually {

          assertRecordStored(
            id = s"$id work",
            version = 1,
            expectedMetadata = GoobiRecordMetadata(eventTime),
            expectedContents = contents,
            dao = dao,
            bucket = bucket)
        }
    }
  }

  it("doesn't overwrite a newer work with an older work") {
    withGoobiReaderWorkerService(s3Client) {
      case (bucket, QueuePair(queue, _), _, dao, vhs) =>
        val contentStream = new ByteArrayInputStream(contents.getBytes)
        vhs.update(id)(
          ifNotExisting = (contentStream, GoobiRecordMetadata(eventTime)))(
          ifExisting = (t, m) => (t, m))
        eventually {
          val expectedMetadata = GoobiRecordMetadata(eventTime)
          assertRecordStored(
            id = id,
            version = 1,
            expectedMetadata = expectedMetadata,
            expectedContents = contents,
            dao = dao,
            bucket = bucket)
        }

        s3Client.putObject(bucket.name, sourceKey, contents)

        val olderEventTime = eventTime.minus(1, ChronoUnit.HOURS)

        sendNotificationToSQS(
          queue = queue,
          body = createS3Notification(sourceKey, bucket.name, olderEventTime)
        )

        eventually {
          val expectedMetadata = GoobiRecordMetadata(eventTime)
          assertRecordStored(
            id = id,
            version = 1,
            expectedMetadata,
            expectedContents = contents,
            dao = dao,
            bucket = bucket
          )
        }
    }
  }

  it("overwrites an older work with an newer work") {
    withGoobiReaderWorkerService(s3Client) {
      case (bucket, QueuePair(queue, _), _, table, vhs) =>
        val contentStream = new ByteArrayInputStream(contents.getBytes)
        vhs.update(id)(
          ifNotExisting = (contentStream, GoobiRecordMetadata(eventTime)))(
          ifExisting = (t, m) => (t, m))
        eventually {
          assertRecordStored(
            id = id,
            version = 1,
            GoobiRecordMetadata(eventTime),
            contents,
            table,
            bucket)
        }

        s3Client.putObject(bucket.name, sourceKey, updatedContents)
        val newerEventTime = eventTime.plus(1, ChronoUnit.HOURS)

        sendNotificationToSQS(
          queue = queue,
          body = createS3Notification(sourceKey, bucket.name, newerEventTime)
        )

        eventually {
          val expectedMetadata = GoobiRecordMetadata(newerEventTime)
          assertRecordStored(
            id = id,
            version = 2,
            expectedMetadata,
            updatedContents,
            table,
            bucket)
        }
    }
  }

  it("fails gracefully if Json cannot be parsed") {
    withGoobiReaderWorkerService(s3Client) {
      case (bucket, QueuePair(queue, dlq), metricsSender, table, _) =>
        sendNotificationToSQS(
          queue = queue,
          body = "NotJson"
        )

        eventually {
          assertMessageSentToDlq(queue, dlq)
          assertUpdateNotSaved(bucket, table)
          verify(metricsSender, times(3))
            .incrementCount(endsWith("_recognisedFailure"))
        }
    }
  }

  it("does not fail gracefully if content cannot be fetched") {
    withGoobiReaderWorkerService(s3Client) {
      case (bucket, QueuePair(queue, dlq), metricsSender, table, _) =>
        val sourceKey = "NotThere.xml"

        sendNotificationToSQS(
          queue = queue,
          body = createS3Notification(sourceKey, bucket.name, eventTime)
        )

        eventually {
          assertMessageSentToDlq(queue, dlq)
          assertUpdateNotSaved(bucket, table)
          verify(metricsSender, times(3))
            .incrementCount(endsWith("_failure"))
        }
    }
  }

  it("does not fail gracefully when there is an unexpected failure") {
    val mockClient = mock[AmazonS3Client]
    val expectedException = new RuntimeException("Failed!")
    when(mockClient.getObject(any[String], any[String]))
      .thenThrow(expectedException)
    withGoobiReaderWorkerService(mockClient) {
      case (bucket, QueuePair(queue, dlq), metricsSender, table, _) =>
        val sourceKey = "any.xml"
        sendNotificationToSQS(
          queue = queue,
          body = createS3Notification(sourceKey, bucket.name, eventTime)
        )

        eventually {
          assertMessageSentToDlq(queue, dlq)
          assertUpdateNotSaved(bucket, table)
          verify(metricsSender, times(3))
            .incrementCount(endsWith("_failure"))
        }
    }
  }

  private def assertRecordStored(id: String,
                                 version: Int,
                                 expectedMetadata: GoobiRecordMetadata,
                                 expectedContents: String,
                                 dao: GoobiDao,
                                 bucket: Bucket): Assertion =
    inside(dao.get(id).get.get) {
      case Entry(actualId, actualVersion, location, actualMetadata) =>
        actualId shouldBe id
        actualVersion shouldBe version
        actualMetadata shouldBe expectedMetadata
        getContentFromS3(location) shouldBe expectedContents
    }

  private def assertMessageSentToDlq(queue: Queue, dlq: Queue) = {
    assertQueueEmpty(queue)
    assertQueueHasSize(dlq, 1)
  }

  private def assertUpdateNotSaved(bucket: Bucket, dao: GoobiDao): Assertion = {
    // TODO: Add a way to check a dao is empty
    listKeysInBucket(bucket) shouldBe empty
  }

  private def withGoobiReaderWorkerService[R](s3Client: AmazonS3)(
    testWith: TestWith[(Bucket,
                        QueuePair,
                        MetricsSender,
                        MemoryVersionedDao[String, Entry[String, GoobiRecordMetadata]],
                        GoobiVHS),
                       R]): R =
    withActorSystem { implicit actorSystem =>
      withLocalSqsQueueAndDlq {
        case queuePair@QueuePair(queue, dlq) =>
          withMockMetricsSender { mockMetricsSender =>
            withSQSStream[NotificationMessage, R](
              queue = queue,
              metricsSender = mockMetricsSender) { sqsStream =>
              withLocalS3Bucket[R] { bucket =>
                val dao = MemoryVersionedDao[String, Entry[String, GoobiRecordMetadata]]
                val vhs = createVHS(bucket, dao)

                val service = new GoobiReaderWorkerService(
                  s3Client = s3Client,
                  sqsStream = sqsStream,
                  vhs = vhs
                )

                service.run()

                testWith((bucket, queuePair, mockMetricsSender, dao, vhs))
              }
            }
          }
      }
    }
}
