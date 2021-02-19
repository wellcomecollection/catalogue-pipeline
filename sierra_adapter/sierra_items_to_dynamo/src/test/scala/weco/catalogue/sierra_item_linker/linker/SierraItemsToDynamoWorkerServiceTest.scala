package weco.catalogue.sierra_item_linker.linker

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraItemNumber,
  SierraItemRecord
}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import weco.catalogue.sierra_item_linker.fixtures.ItemLinkerWorkerServiceFixture

class SierraItemsToDynamoWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with ScalaFutures
    with SierraGenerators
    with ItemLinkerWorkerServiceFixture
    with SierraAdapterHelpers {

  it("reads a sierra record from SQS and inserts it into DynamoDB") {
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

    val expectedLink = SierraItemLinker
      .mergeLinks(existingLink = SierraItemLink(record1), newRecord = record2)
      .get

    val store = MemoryVersionedStore[SierraItemNumber, SierraItemLink](
      initialEntries = Map(
        Version(record1.id, 1) -> SierraItemLink(record1)
      )
    )

    val messageSender = new MemoryMessageSender

    withLocalSqsQueue() { queue =>
      withWorkerService(queue, store, messageSender = messageSender) { _ =>
        sendNotificationToSQS(queue = queue, message = record2)

        eventually {
          messageSender.getMessages[SierraItemRecord] shouldBe Seq(
            record2.copy(unlinkedBibIds = expectedLink.unlinkedBibIds)
          )
        }
      }
    }
  }

  it("only applies an update once, even if it's sent multiple times") {
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

    val expectedLink = SierraItemLinker
      .mergeLinks(existingLink = SierraItemLink(record1), newRecord = record2)
      .get

    val store = MemoryVersionedStore[SierraItemNumber, SierraItemLink](
      initialEntries = Map(
        Version(record1.id, 1) -> SierraItemLink(record1)
      )
    )

    val messageSender = new MemoryMessageSender

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorkerService(queue, store, messageSender = messageSender) { _ =>
          (1 to 5).foreach { _ =>
            sendNotificationToSQS(queue = queue, message = record2)
          }

          eventually {
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)

            messageSender.getMessages[SierraItemRecord] shouldBe Seq(
              record2.copy(unlinkedBibIds = expectedLink.unlinkedBibIds)
            )
          }
        }
    }
  }

  it("records a failure if it receives an invalid message") {
    val metrics = new MemoryMetrics()
    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorkerService(queue, metrics = metrics) { _ =>
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
