package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import org.mockito.Mockito.{never, verify}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.platform.sierra_items_to_dynamo.fixtures.WorkerServiceFixture
import uk.ac.wellcome.platform.sierra_items_to_dynamo.merger.SierraItemRecordMerger
import uk.ac.wellcome.sierra_adapter.model.{SierraGenerators, SierraItemRecord}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.Version

import scala.concurrent.Future

class SierraItemsToDynamoWorkerServiceTest
    extends AnyFunSpec
    with SNS
    with SQS
    with Matchers
    with Eventually
    with IntegrationPatience
    with ScalaFutures
    with SierraGenerators
    with WorkerServiceFixture
    with SierraAdapterHelpers
    with MockitoSugar {

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

    val expectedRecord = SierraItemRecordMerger.mergeItems(
      existingRecord = record1,
      updatedRecord = record2
    )
    val store =
      createStore(Map(Version(record1.id.withoutCheckDigit, 1) -> record1))

    withLocalSqsQueue() { queue =>
      withWorkerService(queue, store) { _ =>
        sendNotificationToSQS(queue = queue, message = record2)

        eventually {
          assertStored[SierraItemRecord](
            record1.id.withoutCheckDigit,
            expectedRecord,
            store
          )
        }
      }
    }
  }

  it("records a failure if it receives an invalid message") {
    val store = createStore[SierraItemRecord]()
    val mockMetricsSender = mock[Metrics[Future, StandardUnit]]
    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorkerService(queue, store, mockMetricsSender) { _ =>
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
