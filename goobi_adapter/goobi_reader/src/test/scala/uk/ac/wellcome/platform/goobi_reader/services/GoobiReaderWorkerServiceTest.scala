package uk.ac.wellcome.platform.goobi_reader.services

import java.io.InputStream
import java.time.Instant
import java.time.temporal.ChronoUnit

import org.mockito.Matchers.endsWith
import org.mockito.Mockito.{times, verify}
import org.scalatest.FunSpec
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.platform.goobi_reader.fixtures.GoobiReaderFixtures
import uk.ac.wellcome.platform.goobi_reader.models.GoobiRecordMetadata
import uk.ac.wellcome.storage._
import uk.ac.wellcome.storage.generators.ObjectLocationGenerators
import uk.ac.wellcome.storage.memory.{MemoryObjectStore, MemoryStorageBackend}
import uk.ac.wellcome.storage.streaming.CodecInstances._

class GoobiReaderWorkerServiceTest
    extends FunSpec
    with Eventually
    with GoobiReaderFixtures
    with ObjectLocationGenerators
    with IntegrationPatience {

  private val id = "mets-0001"
  private val contents = "muddling the machinations of morose METS"
  private val updatedContents = "Updated contents"
  private val eventTime = Instant.parse("2018-01-01T01:00:00.000Z")

  val olderEventTime: Instant = eventTime.minus(1, ChronoUnit.HOURS)
  val newerEventTime: Instant = eventTime.plus(1, ChronoUnit.HOURS)

  it("processes a notification") {
    val s3Store = createStore

    withGoobiReaderWorkerService(s3Store) {
      case (QueuePair(queue, _), _, dao, store) =>
        val location = putString(s3Store, id, contents)

        sendNotificationToSQS(
          queue = queue,
          body = anS3Notification(location, eventTime)
        )

        eventually {
          assertRecordStored(
            id = id,
            version = 1,
            expectedMetadata = GoobiRecordMetadata(eventTime),
            expectedContents = contents,
            dao = dao,
            store = store
          )
        }
    }
  }

  it("ingests an object with a space in the s3Key") {
    val s3Store = createStore

    withGoobiReaderWorkerService(s3Store) {
      case (QueuePair(queue, _), _, dao, store) =>
        val location = putString(s3Store, id = s"$id work", contents)

        val encodedLocation = location.copy(
          key = java.net.URLEncoder.encode(location.key, "utf-8")
        )

        sendNotificationToSQS(
          queue = queue,
          body = anS3Notification(encodedLocation, eventTime)
        )

        eventually {
          assertRecordStored(
            id = s"$id work",
            version = 1,
            expectedMetadata = GoobiRecordMetadata(eventTime),
            expectedContents = contents,
            dao = dao,
            store = store)
        }
    }
  }

  it("doesn't overwrite a newer input stream with an older input stream") {
    val s3Store = createStore

    withGoobiReaderWorkerService(s3Store) {
      case (QueuePair(queue, _), _, dao, store) =>
        val vhs = createVhs(dao, store)
        vhs.update(id)(
          ifNotExisting =
            (stringStream(contents), GoobiRecordMetadata(newerEventTime))
        )(
          ifExisting = (t, m) => (t, m)
        ) shouldBe a[Right[_, _]]

        val location = putString(s3Store, id, "a different string")

        sendNotificationToSQS(
          queue = queue,
          body = anS3Notification(location, olderEventTime)
        )

        // Give it time to overwrite the stream, if it's going to
        Thread.sleep(2000)

        eventually {
          assertRecordStored(
            id = id,
            version = 1,
            expectedMetadata = GoobiRecordMetadata(newerEventTime),
            expectedContents = contents,
            dao = dao,
            store = store
          )
        }
    }
  }

  it("overwrites an older input stream with an newer input stream") {
    val s3Store = createStore

    withGoobiReaderWorkerService(s3Store) {
      case (QueuePair(queue, _), _, dao, store) =>
        val vhs = createVhs(dao, store)
        vhs.update(id)(
          ifNotExisting =
            (stringStream(contents), GoobiRecordMetadata(olderEventTime))
        )(
          ifExisting = (t, m) => (t, m)
        ) shouldBe a[Right[_, _]]

        val location = putString(s3Store, id, updatedContents)

        sendNotificationToSQS(
          queue = queue,
          body = anS3Notification(location, newerEventTime)
        )

        eventually {
          assertRecordStored(
            id = id,
            version = 2,
            expectedMetadata = GoobiRecordMetadata(newerEventTime),
            expectedContents = updatedContents,
            dao = dao,
            store = store
          )
        }
    }
  }

  it("fails gracefully if Json cannot be parsed") {
    withGoobiReaderWorkerService() {
      case (QueuePair(queue, dlq), metricsSender, dao, store) =>
        sendNotificationToSQS(
          queue = queue,
          body = "NotJson"
        )

        eventually {
          assertMessageSentToDlq(queue, dlq)
          assertUpdateNotSaved(store, dao)
          verify(metricsSender, times(3))
            .incrementCount(endsWith("_recognisedFailure"))
        }
    }
  }

  it("does not fail gracefully if content cannot be fetched") {
    withGoobiReaderWorkerService() {
      case (QueuePair(queue, dlq), metricsSender, dao, store) =>
        sendNotificationToSQS(
          queue = queue,
          body = anS3Notification(createObjectLocation, eventTime)
        )

        eventually {
          assertMessageSentToDlq(queue, dlq)
          assertUpdateNotSaved(store, dao)
          verify(metricsSender, times(3))
            .incrementCount(endsWith("_failure"))
        }
    }
  }

  it("does not fail gracefully when there is an unexpected failure") {
    val s3Store = new MemoryObjectStore[InputStream]() {
      override def get(
        objectLocation: ObjectLocation): Either[ReadError, InputStream] =
        Left(BackendReadError(new Throwable("BOOM!")))
    }

    withGoobiReaderWorkerService(s3Store) {
      case (QueuePair(queue, dlq), metricsSender, dao, store) =>
        sendNotificationToSQS(
          queue = queue,
          body = anS3Notification(createObjectLocation, eventTime)
        )

        eventually {
          assertMessageSentToDlq(queue, dlq)
          assertUpdateNotSaved(store, dao)
          verify(metricsSender, times(3))
            .incrementCount(endsWith("_failure"))
        }
    }
  }

  private def assertMessageSentToDlq(queue: Queue, dlq: Queue) = {
    assertQueueEmpty(queue)
    assertQueueHasSize(dlq, 1)
  }

  private def assertUpdateNotSaved(store: GoobiStore, dao: GoobiDao) = {
    dao.entries shouldBe empty
    store.storageBackend
      .asInstanceOf[MemoryStorageBackend]
      .storage shouldBe empty
  }
}
