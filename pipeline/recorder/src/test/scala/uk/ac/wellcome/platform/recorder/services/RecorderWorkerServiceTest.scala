package uk.ac.wellcome.platform.recorder.services

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.recorder.fixtures.WorkerServiceFixture
import uk.ac.wellcome.storage._
import uk.ac.wellcome.storage.memory.{MemoryConditionalUpdateDao, MemoryDao, MemoryVersionedDao}
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry}

class RecorderWorkerServiceTest
    extends FunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with WorkerServiceFixture
    with WorksGenerators {

  it("records an UnidentifiedWork") {
    val dao = createDao
    val store = createStore
    val vhs = createVhs(dao, store)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      val work = createUnidentifiedWork
      sendMessage[TransformedBaseWork](queue = queue, obj = work)
      withWorkerService(vhs, messageSender, queue) { _ =>
        eventually {
          assertStoredSingleWork(messageSender, dao, store, work)
        }
      }
    }
  }

  it("stores UnidentifiedInvisibleWorks") {
    val dao = createDao
    val store = createStore
    val vhs = createVhs(dao, store)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(vhs, messageSender, queue) { _ =>
        val invisibleWork = createUnidentifiedInvisibleWork
        sendMessage[TransformedBaseWork](queue = queue, invisibleWork)
        eventually {
          assertStoredSingleWork(messageSender, dao, store, invisibleWork)
        }
      }
    }
  }

  it("doesn't overwrite a newer work with an older work") {
    val olderWork = createUnidentifiedWork
    val newerWork = olderWork.copy(version = 10, title = "A nice new thing")

    val dao = createDao
    val store = createStore
    val vhs = createVhs(dao, store)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(vhs, messageSender, queue) { _ =>
        sendMessage[TransformedBaseWork](queue = queue, newerWork)
        eventually {
          assertStoredSingleWork(messageSender, dao, store, newerWork)
          sendMessage[TransformedBaseWork](
            queue = queue,
            obj = olderWork)
          eventually {
            assertStoredSingleWork(messageSender, dao, store, newerWork)
          }
        }
      }
    }
  }

  it("overwrites an older work with an newer work") {
    val olderWork = createUnidentifiedWork
    val newerWork = olderWork.copy(version = 10, title = "A nice new thing")

    val dao = createDao
    val store = createStore
    val vhs = createVhs(dao, store)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(vhs, messageSender, queue) { _ =>
        sendMessage[TransformedBaseWork](queue = queue, obj = olderWork)
        eventually {
          assertStoredSingleWork(messageSender, dao, store, olderWork)
          sendMessage[TransformedBaseWork](queue = queue, obj = newerWork)
          eventually {
            assertStoredSingleWork(
              messageSender,
              dao,
              store,
              newerWork,
              expectedVersion = 2)
          }
        }
      }
    }
  }

  it("fails if saving to the object store fails") {
    val exception = new Throwable("BOOM!")

    val brokenStore = new RecorderStore {
      override def put(namespace: String)(
        input: TransformedBaseWork,
        keyPrefix: KeyPrefix,
        keySuffix: KeySuffix,
        userMetadata: Map[String, String]): Either[WriteError, ObjectLocation] =
        Left(new BackendWriteError(exception))
    }

    val vhs = createVhs(store = brokenStore)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        withWorkerService(vhs, messageSender, queue) { _ =>
          val work = createUnidentifiedWork
          sendMessage[TransformedBaseWork](queue = queue, obj = work)
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
          }
        }
    }
  }

  it("fails if saving to the dao fails") {
    val exception = new Throwable("BOOM!")

    val brokenConditionalDao = new MemoryConditionalUpdateDao[String, Entry[String, EmptyMetadata]](
      underlying = new MemoryDao[String, Entry[String, EmptyMetadata]]()
    ) {
      override def put(t: Entry[String, EmptyMetadata]): PutResult =
        Left(new DaoWriteError(exception))
    }

    val brokenDao = new MemoryVersionedDao(brokenConditionalDao)

    val vhs = createVhs(dao = brokenDao)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        withWorkerService(vhs, messageSender, queue) { _ =>
          val work = createUnidentifiedWork
          sendMessage[TransformedBaseWork](queue = queue, obj = work)
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)
          }
        }
    }
  }
}
