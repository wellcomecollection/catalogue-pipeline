package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import org.mockito.Mockito.{never, verify}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.platform.sierra_items_to_dynamo.fixtures.WorkerServiceFixture
import uk.ac.wellcome.platform.sierra_items_to_dynamo.merger.SierraItemRecordMerger
import uk.ac.wellcome.storage.vhs.EmptyMetadata

class SierraItemsToDynamoWorkerServiceTest
    extends FunSpec
    with SQS
    with Matchers
    with Eventually
    with SierraGenerators
    with WorkerServiceFixture
    with IntegrationPatience {

  it("reads a sierra record from SQS and inserts it into VHS") {
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

    val expectedRecord = SierraItemRecordMerger.mergeItems(
      existingRecord = record1,
      updatedRecord = record2
    )

    val dao = createDao
    val store = createItemStore
    val vhs = createItemVhs(dao, store)

    val messageSender = new MemoryMessageSender()

    storeSingleRecord(record1, vhs = vhs)

    withLocalSqsQueue { queue =>
      withWorkerService(queue, dao, store, messageSender) { _ =>
        sendNotificationToSQS(queue = queue, message = record2)

        eventually {
          assertStored(expectedRecord, vhs = vhs)
        }
      }
    }
  }

  it("records a failure if it receives an invalid message") {
    val dao = createDao
    val store = createItemStore

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueueAndDlq { case QueuePair(queue, dlq) =>
      withMockMetricsSender { mockMetricsSender =>
        withWorkerService(queue, dao, store, messageSender, mockMetricsSender) { _ =>
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
            verify(mockMetricsSender, never()).incrementCount(
              "SierraItemsToDynamoWorkerService_ProcessMessage_failure")
          }
        }
      }
    }
  }

  def storeSingleRecord(
    itemRecord: SierraItemRecord,
    vhs: SierraItemVHS
  ): Assertion = {
    val result =
      vhs.update(id = itemRecord.id.withoutCheckDigit)(
        ifNotExisting = (itemRecord, EmptyMetadata())
      )(
        ifExisting = (existingRecord, existingMetadata) =>
          throw new RuntimeException(
            s"VHS should be empty; got ($existingRecord, $existingMetadata)!")
      )

    result shouldBe a[Right[_, _]]
  }
}
