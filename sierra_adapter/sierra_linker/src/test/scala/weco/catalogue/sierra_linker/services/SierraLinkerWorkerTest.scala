package weco.catalogue.sierra_linker.services

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import weco.catalogue.sierra_linker.fixtures.WorkerFixture
import weco.catalogue.sierra_linker.models.{Link, LinkOps}
import weco.catalogue.source_model.generators.SierraGenerators
import weco.catalogue.source_model.sierra.Implicits._
import weco.catalogue.source_model.sierra.{SierraItemNumber, SierraItemRecord}

class SierraLinkerWorkerTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with ScalaFutures
    with SierraGenerators
    with WorkerFixture {

  it("reads a Sierra record from SQS and stores it") {
    val bibIds = createSierraBibNumbers(count = 5)

    val bibIds1 = List(bibIds(0), bibIds(1), bibIds(2))

    val record1 = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = bibIds1
    )

    val bibIds2 = List(bibIds(2), bibIds(3), bibIds(4))

    val record2 = createSierraItemRecordWith(
      id = record1.id,
      modifiedDate = newerDate,
      bibIds = bibIds2
    )

    val expectedLink = LinkOps.itemLinksOps
      .updateLink(existingLink = Link(record1), newRecord = record2)
      .get

    val store = MemoryVersionedStore[SierraItemNumber, Link](
      initialEntries = Map(
        Version(record1.id, 1) -> Link(record1)
      )
    )

    val messageSender = new MemoryMessageSender

    withLocalSqsQueue() { queue =>
      withItemWorker(queue, store, messageSender = messageSender) { _ =>
        sendNotificationToSQS(queue = queue, message = record2)

        eventually {
          messageSender.getMessages[SierraItemRecord] shouldBe Seq(
            record2.copy(unlinkedBibIds = expectedLink.unlinkedBibIds)
          )
        }
      }
    }
  }

  it("always forwards the newest item, even if it's sent multiple times") {
    val record = createSierraItemRecord

    val messageSender = new MemoryMessageSender

    val store =
      MemoryVersionedStore[SierraItemNumber, Link](initialEntries = Map.empty)

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withItemWorker(queue, store = store, messageSender = messageSender) {
          _ =>
            (1 to 5).foreach { _ =>
              sendNotificationToSQS(queue = queue, message = record)
            }

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)

              messageSender
                .getMessages[SierraItemRecord] shouldBe (1 to 5).map { _ =>
                record
              }
            }
        }
    }
  }

  it("skips an item which is older than the stored link") {
    val bibIds = createSierraBibNumbers(count = 5)

    val bibIds1 = List(bibIds(0), bibIds(1), bibIds(2))

    val record1 = createSierraItemRecordWith(
      modifiedDate = olderDate,
      bibIds = bibIds1
    )

    val bibIds2 = List(bibIds(2), bibIds(3), bibIds(4))

    val record2 = createSierraItemRecordWith(
      id = record1.id,
      modifiedDate = newerDate,
      bibIds = bibIds2
    )

    val store = MemoryVersionedStore[SierraItemNumber, Link](
      initialEntries = Map(
        Version(record2.id, 1) -> Link(record2)
      )
    )

    val messageSender = new MemoryMessageSender

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withItemWorker(queue, store, messageSender = messageSender) { _ =>
          sendNotificationToSQS(queue = queue, message = record1)

          eventually {
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)

            messageSender.messages shouldBe empty
          }
        }
    }
  }

  it("records a failure if it receives an invalid message") {
    val metrics = new MemoryMetrics()

    val store =
      MemoryVersionedStore[SierraItemNumber, Link](initialEntries = Map.empty)

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withItemWorker(queue, store = store, metrics = metrics) { _ =>
          val body =
            """
                    |{
                    | "something": "something"
                    |}
                  """.stripMargin

          sendNotificationToSQS(queue = queue, body = body)

          eventually {
            assertQueueEmpty(queue)
            assertQueueHasSize(dlq, size = 1)
            metrics.incrementedCounts should not contain "SierraItemsToDynamoWorkerService_ProcessMessage_failure"
          }
        }
    }
  }
}
