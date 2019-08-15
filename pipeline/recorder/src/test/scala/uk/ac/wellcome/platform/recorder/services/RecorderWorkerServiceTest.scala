package uk.ac.wellcome.platform.recorder.services

import org.scalatest.concurrent.Eventually
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.recorder.fixtures.WorkerServiceFixture
import uk.ac.wellcome.storage._
import uk.ac.wellcome.storage.memory.{
  MemoryConditionalUpdateDao,
  MemoryVersionedDao
}
import uk.ac.wellcome.storage.streaming.CodecInstances._

class RecorderWorkerServiceTest
    extends FunSpec
    with Matchers
    with Eventually
    with WorkerServiceFixture
    with WorksGenerators {

  it("records an UnidentifiedWork") {
    val messageSender = new MemoryMessageSender()

    val work = createUnidentifiedWork

    withLocalSqsQueue { queue =>
      sendMessage[TransformedBaseWork](queue, work)
      withWorkerService(messageSender, queue) { _ =>
        eventually {
          assertStoredSingleWork(messageSender, work)
        }
      }
    }
  }

  it("stores UnidentifiedInvisibleWorks") {
    val messageSender = new MemoryMessageSender()

    val invisibleWork = createUnidentifiedInvisibleWork

    withLocalSqsQueue { queue =>
      withWorkerService(messageSender, queue) { service =>
        sendMessage[TransformedBaseWork](queue, invisibleWork)
        eventually {
          assertStoredSingleWork(messageSender, invisibleWork)
        }
      }
    }
  }

  it("doesn't overwrite a newer work with an older work") {
    val olderWork = createUnidentifiedWork
    val newerWork = olderWork.copy(version = 10, title = "A nice new thing")

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withMemoryHybridStore { vhs =>
        withWorkerService(vhs, messageSender, queue) { _ =>
          sendMessage[TransformedBaseWork](queue, newerWork)

          eventually {
            assertStoredSingleWork(vhs, messageSender, newerWork)
          }

          sendMessage[TransformedBaseWork](vhs, queue, olderWork)

          eventually {
            assertStoredSingleWork(vhs, messageSender, newerWork)
          }
        }
      }
    }
  }

  it("overwrites an older work with an newer work") {
    val olderWork = createUnidentifiedWork
    val newerWork = olderWork.copy(version = 10, title = "A nice new thing")

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(messageSender, queue) { _ =>
        sendMessage[TransformedBaseWork](queue, olderWork)

        eventually {
          assertStoredSingleWork(messageSender, olderWork)
        }

        sendMessage[TransformedBaseWork](queue, newerWork)

        eventually {
          assertStoredSingleWork(
            messageSender,
            newerWork,
            expectedVhsVersion = 2)
        }
      }
    }
  }

  it("fails if saving to the object store fails") {
    val brokenStore = new RecorderStore {
      override def put(namespace: String)(
        input: TransformedBaseWork,
        keyPrefix: KeyPrefix,
        keySuffix: KeySuffix,
        userMetadata: Map[String, String]): Either[WriteError, ObjectLocation] =
        Left(BackendWriteError(new Throwable("BOOM!")))
    }

    val dao = createDao

    val messageSender = new MemoryMessageSender()

    val work = createUnidentifiedWork

    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        withWorkerService(dao, brokenStore, messageSender, queue) { _ =>
          sendMessage[TransformedBaseWork](queue = queue, obj = work)
          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)

            messageSender.messages shouldBe empty
          }
        }
    }
  }

  it("fails if saving to the versioned dao fails") {
    val brokenDao = new MemoryVersionedDao[String, RecorderEntry](
      underlying = MemoryConditionalUpdateDao[String, RecorderEntry]()
    ) {
      override def put(
        value: RecorderEntry): Either[WriteError, RecorderEntry] =
        Left(DaoWriteError(new Throwable("BOOM!")))
    }

    val store = createStore

    val messageSender = new MemoryMessageSender()

    val work = createUnidentifiedWork

    withLocalSqsQueueAndDlq {
      case QueuePair(queue, dlq) =>
        withWorkerService(brokenDao, store, messageSender, queue) { _ =>
          sendMessage[TransformedBaseWork](queue = queue, obj = work)

          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, 1)

            messageSender.messages shouldBe empty
          }
        }
    }
  }
}
