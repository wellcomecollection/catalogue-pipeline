package uk.ac.wellcome.platform.recorder.services

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.recorder.fixtures.WorkerServiceFixture
import uk.ac.wellcome.storage.Version

class RecorderWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with WorkerServiceFixture
    with JsonAssertions
    with WorksGenerators {

  it("records an UnidentifiedWork") {
    withLocalSqsQueue() { queue =>
      withVHS { vhs =>
        withWorkerService(queue, vhs) { _ =>
          val work = createUnidentifiedWork
          sendMessage[TransformedBaseWork](queue = queue, obj = work)
          eventually {
            assertWorkStored(vhs, work)
          }
        }
      }
    }
  }

  it("stores UnidentifiedInvisibleWorks") {
    withLocalSqsQueue() { queue =>
      withVHS { vhs =>
        withWorkerService(queue, vhs) { _ =>
          val invisibleWork = createUnidentifiedInvisibleWork
          sendMessage[TransformedBaseWork](queue = queue, invisibleWork)
          eventually {
            assertWorkStored(vhs, invisibleWork)
          }
        }
      }
    }
  }

  it("doesn't overwrite a newer work with an older work") {
    withLocalSqsQueue() { queue =>
      withVHS { vhs =>
        withWorkerService(queue, vhs) { _ =>
          val olderWork = createUnidentifiedWork
          val newerWork = olderWork
            .copy(version = 10)
            .withData(data => data.copy(title = Some("A nice new thing")))
          sendMessage[TransformedBaseWork](queue = queue, newerWork)
          eventually { assertWorkStored(vhs, newerWork) }
          sendMessage[TransformedBaseWork](queue = queue, obj = olderWork)
          eventually { assertQueueEmpty(queue) }
          assertWorkStored(vhs, newerWork, 1)
        }
      }
    }
  }

  it("overwrites an older work with an newer work") {
    withLocalSqsQueue() { queue =>
      withVHS { vhs =>
        withWorkerService(queue, vhs) { _ =>
          val olderWork = createUnidentifiedWork
          val newerWork = olderWork
            .copy(version = 10)
            .withData(data => data.copy(title = Some("A nice new thing")))
          sendMessage[TransformedBaseWork](queue = queue, obj = olderWork)
          eventually {
            assertWorkStored(vhs, olderWork)
            sendMessage[TransformedBaseWork](queue = queue, obj = newerWork)
            eventually {
              assertWorkStored(vhs, newerWork, expectedVhsVersion = 1)
            }
          }
        }
      }
    }
  }

  it("fails if saving to the store fails") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueuePair() {
      case SQS.QueuePair(queue, dlq) =>
        withBrokenVHS { vhs =>
          withWorkerService(queue, vhs, messageSender) { _ =>
            val work = createUnidentifiedWork
            sendMessage[TransformedBaseWork](queue = queue, obj = work)
            eventually {
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, 1)
              assertWorkNotStored(vhs, work)
              messageSender.messages shouldBe empty
            }
          }
        }
    }
  }

  it("sends the VHS key to the queue") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withVHS { vhs =>
        withWorkerService(queue, vhs, messageSender) { _ =>
          val work = createUnidentifiedWork
          sendMessage[TransformedBaseWork](queue = queue, obj = work)
          eventually {
            val id = work.sourceIdentifier.toString

            messageSender.getMessages[Version[String, Int]] shouldBe Seq(Version(id, 0))
          }
        }
      }
    }
  }
}
