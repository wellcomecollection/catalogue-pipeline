package weco.tei.adapter;

import io.circe.Encoder
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import weco.akka.fixtures.Akka
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.storage.Version
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryVersionedStore
import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.source_model.tei.{
  TeiChangedMetadata,
  TeiDeletedMetadata,
  TeiIdChangeMessage,
  TeiIdDeletedMessage,
  TeiIdMessage,
  TeiMetadata
}
import weco.catalogue.source_model.Implicits._

import java.time.Instant
import java.time.temporal.ChronoUnit.HOURS
import scala.util.{Failure, Try}
import scala.concurrent.duration._

class TeiAdapterWorkerServiceTest
    extends AnyFunSpec
    with SQS
    with Eventually
    with Akka
    with IntegrationPatience {
  it("processes a message from the tei id extractor") {
    withWorkerService() {
      case (QueuePair(queue, dlq), messageSender, store) =>
        val message = TeiIdChangeMessage(
          "manuscript_1234",
          S3ObjectLocation("bucket", "key.xml"),
          Instant.parse("2021-06-17T11:46:00Z")
        )

        sendNotificationToSQS[TeiIdMessage](queue, message)

        eventually {
          val expectedVersion = Version(message.id, 0)
          val expectedMetadata = TeiChangedMetadata(
            s3Location = message.s3Location,
            time = message.timeModified
          )
          isSent(messageSender, expectedVersion, expectedMetadata)
          isStored(store, expectedVersion, expectedMetadata)
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  it("updates the version if it gets a new message for an id already stored") {
    val storedTime = Instant.parse("2021-06-17T11:46:00Z")
    val storedVersion = Version("manuscript_1234", 0)
    val storedMap =
      Map(
        storedVersion -> TeiChangedMetadata(
          S3ObjectLocation("bucket", "key.xml"),
          storedTime))
    withWorkerService(initialData = storedMap) {
      case (QueuePair(queue, dlq), messageSender, store) =>
        val message =
          TeiIdChangeMessage(
            storedVersion.id,
            S3ObjectLocation("bucket", "anotherkey.xml"),
            storedTime.plus(2, HOURS))

        sendNotificationToSQS[TeiIdMessage](queue, message)

        eventually {
          val expectedVersion = Version(message.id, 1)
          val expectedMetadata = TeiChangedMetadata(
            s3Location = message.s3Location,
            time = message.timeModified
          )

          isSent(messageSender, expectedVersion, expectedMetadata)
          isStored(store, expectedVersion, expectedMetadata)

          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  it(
    "doesn't replace the stored metadata if the time on the message is older than the time on the store"
  ) {
    val storedObjectLocation = S3ObjectLocation("bucket", "key.xml")
    val storedTime = Instant.parse("2021-06-17T11:46:00Z")
    val storedVersion = Version("manuscript_1234", 0)
    val storedMap =
      Map(storedVersion -> TeiChangedMetadata(storedObjectLocation, storedTime))

    withWorkerService(initialData = storedMap) {
      case (QueuePair(queue, dlq), messageSender, store) =>
        val messageObjectLocation = S3ObjectLocation("bucket", "anotherkey.xml")
        val messageTime = storedTime.minus(2, HOURS)
        val message =
          TeiIdChangeMessage(
            storedVersion.id,
            messageObjectLocation,
            messageTime)

        sendNotificationToSQS[TeiIdMessage](queue, message)

        eventually {
          val expectedVersion = Version(message.id, 1)

          val expectedMetadata = TeiChangedMetadata(
            s3Location = storedObjectLocation,
            time = storedTime
          )

          isSent(messageSender, expectedVersion, expectedMetadata)
          isStored(store, expectedVersion, expectedMetadata)

          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  it("handles a delete message") {
    val storedObjectLocation = S3ObjectLocation("bucket", "key.xml")
    val storedTime = Instant.parse("2021-06-17T11:46:00Z")
    val storedVersion = Version("manuscript_1234", 0)
    val storedMap =
      Map(storedVersion -> TeiChangedMetadata(storedObjectLocation, storedTime))

    withWorkerService(initialData = storedMap) {
      case (QueuePair(queue, dlq), messageSender, store) =>
        val messageTime = storedTime.plus(2, HOURS)
        val message = TeiIdDeletedMessage(storedVersion.id, messageTime)

        sendNotificationToSQS[TeiIdMessage](queue, message)

        eventually {
          val expectedVersion = Version(message.id, 1)
          val expectedMetadata = TeiDeletedMetadata(time = messageTime)

          isSent(messageSender, expectedVersion, expectedMetadata)
          isStored(store, expectedVersion, expectedMetadata)

          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  it("ignores a delete message if the time is before the stored change time") {
    val storedObjectLocation = S3ObjectLocation("bucket", "key.xml")
    val storedTime = Instant.parse("2021-06-17T11:46:00Z")
    val storedVersion = Version("manuscript_1234", 0)
    val storedMetadata = TeiChangedMetadata(storedObjectLocation, storedTime)
    withWorkerService(initialData = Map(storedVersion -> storedMetadata)) {
      case (QueuePair(queue, dlq), messageSender, store) =>
        val messageTime = storedTime.minus(2, HOURS)
        val message = TeiIdDeletedMessage(storedVersion.id, messageTime)

        sendNotificationToSQS[TeiIdMessage](queue, message)

        eventually {
          val expectedVersion = Version(message.id, 1)
          isSent(messageSender, expectedVersion, storedMetadata)
          isStored(store, expectedVersion, storedMetadata)

          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  it("ignores a delete message if the time is equal to the stored change time") {
    val storedObjectLocation = S3ObjectLocation("bucket", "key.xml")
    val storedTime = Instant.parse("2021-06-17T11:46:00Z")
    val storedVersion = Version("manuscript_1234", 0)
    val storedMetadata = TeiChangedMetadata(storedObjectLocation, storedTime)
    withWorkerService(initialData = Map(storedVersion -> storedMetadata)) {
      case (QueuePair(queue, dlq), messageSender, store) =>
        val message = TeiIdDeletedMessage(storedVersion.id, storedTime)

        sendNotificationToSQS[TeiIdMessage](queue, message)

        eventually {
          val expectedVersion = Version(message.id, 1)
          isSent(messageSender, expectedVersion, storedMetadata)
          isStored(store, expectedVersion, storedMetadata)
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  it("if sending a change message fails the message is retried") {
    withWorkerService(messageSender = failingOnceMessageSender) {
      case (QueuePair(queue, dlq), messageSender, store) =>
        val bucket = "bucket"
        val key = "key.xml"
        val message = TeiIdChangeMessage(
          "manuscript_1234",
          S3ObjectLocation(bucket, key),
          Instant.parse("2021-06-17T11:46:00Z")
        )

        sendNotificationToSQS[TeiIdMessage](queue, message)

        eventually {
          val expectedVersion = Version(message.id, 1)
          val expectedMetadata = TeiChangedMetadata(
            s3Location = message.s3Location,
            time = message.timeModified
          )
          isSent(messageSender, expectedVersion, expectedMetadata)
          isStored(store, expectedVersion, expectedMetadata)
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  it("if sending a delete message fails the message is retried") {
    val storedObjectLocation = S3ObjectLocation("bucket", "key.xml")
    val storedTime = Instant.parse("2021-06-17T11:46:00Z")
    val storedVersion = Version("manuscript_1234", 0)
    val storedData =
      Map(storedVersion -> TeiChangedMetadata(storedObjectLocation, storedTime))

    withWorkerService(
      messageSender = failingOnceMessageSender,
      initialData = storedData
    ) {
      case (QueuePair(queue, dlq), messageSender, store) =>
        val messageTime = storedTime.plus(2, HOURS)
        val message = TeiIdDeletedMessage(storedVersion.id, messageTime)

        sendNotificationToSQS[TeiIdMessage](queue, message)

        eventually {
          val expectedVersion = Version(message.id, 2)
          val expectedMetadata = TeiDeletedMetadata(time = messageTime)

          isSent(messageSender, expectedVersion, expectedMetadata)
          isStored(store, expectedVersion, expectedMetadata)
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  it(
    "if it receives a message changed and a message deleted with the same time, the message deleted takes precedence") {
    val storedObjectLocation = S3ObjectLocation("bucket", "key.xml")
    val storedTime = Instant.parse("2021-06-17T11:46:00Z")
    val storedVersion = Version("manuscript_1234", 0)
    val storedMap =
      Map(storedVersion -> TeiChangedMetadata(storedObjectLocation, storedTime))

    withWorkerService(initialData = storedMap) {
      case (QueuePair(queue, dlq), messageSender, store) =>
        val messageTime = storedTime.plus(2, HOURS)
        val changedMessage = TeiIdChangeMessage(
          storedVersion.id,
          S3ObjectLocation("bucket", "anotherkey.xml"),
          messageTime)
        val deletedMessage = TeiIdDeletedMessage(storedVersion.id, messageTime)

        sendNotificationToSQS[TeiIdMessage](queue, deletedMessage)
        sendNotificationToSQS[TeiIdMessage](queue, changedMessage)

        eventually {
          val expectedVersions =
            List(Version(changedMessage.id, 1), Version(changedMessage.id, 2))
          val expectedMetadata = TeiChangedMetadata(
            s3Location = changedMessage.s3Location,
            time = messageTime)

          messageSender
            .getMessages[TeiSourcePayload] should contain theSameElementsAs expectedVersions
            .map(
              version =>
                TeiSourcePayload(
                  version.id,
                  expectedMetadata,
                  version.version
              ))
          messageSender
            .getMessages[Version[String, Int]]() should contain theSameElementsAs expectedVersions

          isStored(store, Version(changedMessage.id, 2), expectedMetadata)

          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
    }
  }

  def failingOnceMessageSender = new MemoryMessageSender {
    var attempts = 0
    override def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] = {
      if (attempts < 1) {

        attempts += 1
        Failure(new RuntimeException("BOOOOOM!"))
      } else {
        super.sendT(t)
      }
    }
  }

  private def isStored(store: MemoryVersionedStore[String, TeiMetadata],
                       expectedVersion: Version[String, Int],
                       expectedMetadata: TeiMetadata) = {
    val stored = store.getLatest(expectedVersion.id).right.get
    stored.identifiedT shouldBe expectedMetadata
    stored.id shouldBe expectedVersion
  }

  private def isSent(messageSender: MemoryMessageSender,
                     expectedVersion: Version[String, Int],
                     expectedMetadata: TeiMetadata) = {
    messageSender
      .getMessages[TeiSourcePayload] should contain only TeiSourcePayload(
      expectedVersion.id,
      expectedMetadata,
      expectedVersion.version
    )
    messageSender
      .getMessages[Version[String, Int]]() should contain only expectedVersion
  }

  def withWorkerService[R](
    messageSender: MemoryMessageSender = new MemoryMessageSender(),
    initialData: Map[Version[String, Int], TeiMetadata] = Map.empty
  )(
    testWith: TestWith[
      (
        QueuePair,
        MemoryMessageSender,
        MemoryVersionedStore[String, TeiMetadata]
      ),
      R
    ]
  ): R =
    withLocalSqsQueuePair() {
      case q @ QueuePair(queue, dlq) =>
        withActorSystem { implicit ac =>
          implicit val ec = ac.dispatcher
          withSQSStream(queue) { stream: SQSStream[NotificationMessage] =>
            val store: MemoryVersionedStore[String, TeiMetadata] =
              MemoryVersionedStore(initialData)
            val service =
              new TeiAdapterWorkerService(
                stream,
                messageSender,
                store,
                10,
                100 milliseconds)
            service.run()
            testWith((q, messageSender, store))
          }
        }
    }
}
