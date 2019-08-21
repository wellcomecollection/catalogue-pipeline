package uk.ac.wellcome.platform.recorder.services

//import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorksGenerators
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.monitoring.fixtures.MetricsSenderFixture
import uk.ac.wellcome.platform.recorder.fixtures.WorkerServiceFixture
import uk.ac.wellcome.platform.recorder.EmptyMetadata

import uk.ac.wellcome.storage.fixtures.S3Fixtures.Bucket
import uk.ac.wellcome.storage.{Version, Identified}
import uk.ac.wellcome.storage.store.HybridStoreEntry

import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture

class RecorderWorkerServiceTest
    extends FunSpec
    with Matchers
    with MockitoSugar
    with Akka
    with SQS
    with ScalaFutures
    with BigMessagingFixture
    with MetricsSenderFixture
    //with IntegrationPatience
    with WorkerServiceFixture
    with WorksGenerators {

  it("records an UnidentifiedWork") {
    withLocalSnsTopic { topic =>
      withLocalS3Bucket { storageBucket =>
        withLocalSqsQueue { queue =>
          withLocalSnsTopic { topic =>
            val work = createUnidentifiedWork
            sendMessage[TransformedBaseWork](queue = queue, obj = work)
            withWorkerService(storageBucket, topic, queue) { case  (service, vhs) =>
              eventually {
                assertWorkStored(vhs, work)
              }
            }
          }
        }
      }
    }
  }

  it("stores UnidentifiedInvisibleWorks") {
    withLocalSnsTopic { topic =>
      withLocalS3Bucket { storageBucket =>
        withLocalSqsQueue { queue =>
          withLocalSnsTopic { topic =>
            withWorkerService(storageBucket, topic, queue) { case (service, vhs) =>
              val invisibleWork = createUnidentifiedInvisibleWork
              sendMessage[TransformedBaseWork](queue = queue, invisibleWork)
              eventually {
                assertWorkStored(vhs, invisibleWork)
              }
            }
          }
        }
      }
    }
  }

  it("doesn't overwrite a newer work with an older work") {
    val olderWork = createUnidentifiedWork
    val newerWork = olderWork.copy(version = 10, title = "A nice new thing")
    withLocalSnsTopic { topic =>
      withLocalS3Bucket { storageBucket =>
        withLocalSqsQueue { queue =>
          withLocalSnsTopic { topic =>
            withWorkerService(storageBucket, topic, queue) { case (service, vhs) =>
              sendMessage[TransformedBaseWork](queue = queue, newerWork)
              eventually {
                assertWorkStored(vhs, newerWork)
                sendMessage[TransformedBaseWork](
                  queue = queue,
                  obj = olderWork)
                eventually {
                  assertWorkStored(vhs, newerWork)
                }
              }
            }
          }
        }
      }
    }
  }

  it("overwrites an older work with an newer work") {
    val olderWork = createUnidentifiedWork
    val newerWork = olderWork.copy(version = 10, title = "A nice new thing")
    withLocalS3Bucket { storageBucket =>
      withLocalSqsQueue { queue =>
        withLocalSnsTopic { topic =>
          withWorkerService(storageBucket, topic, queue) { case (service, vhs) =>
            sendMessage[TransformedBaseWork](queue = queue, obj = olderWork)
            eventually {
              assertWorkStored(vhs, olderWork)
              sendMessage[TransformedBaseWork](queue = queue, obj = newerWork)
              eventually {
                assertWorkStored(
                  vhs,
                  newerWork,
                  expectedVhsVersion = 1)
              }
            }
          }
        }
      }
    }
  }

  /*
  it("fails if saving to S3 fails") {
    withLocalSnsTopic { topic =>
      val badBucket = Bucket(name = "bad-bukkit")
      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withWorkerService(badBucket, topic, queue) { case (service, vhs) =>
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

  it("returns a failed Future if saving to DynamoDB fails") {
    val badTable = Table(name = "bad-table", index = "bad-index")
    withLocalSnsTopic { topic =>
      withLocalS3Bucket { storageBucket =>
        withLocalSqsQueueAndDlq {
          case QueuePair(queue, dlq) =>
            withWorkerService(badTable, storageBucket, topic, queue) { _ =>
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
  }
  */

  private def assertWorkStored[T <: TransformedBaseWork](
    vhs: RecorderVhs,
    work: T,
    expectedVhsVersion: Int = 0) = {

    val id = work.sourceIdentifier.toString
    vhs.getLatest(id) shouldBe
      Right(Identified(
        Version(id, expectedVhsVersion),
        HybridStoreEntry(work, EmptyMetadata())))
  }
}
