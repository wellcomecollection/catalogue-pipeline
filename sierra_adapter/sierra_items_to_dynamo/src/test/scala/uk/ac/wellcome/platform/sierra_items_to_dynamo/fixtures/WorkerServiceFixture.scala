package uk.ac.wellcome.platform.sierra_items_to_dynamo.fixtures

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.platform.sierra_items_to_dynamo.services.{VHSInserter, SierraItemsToDynamoWorkerService}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers

trait WorkerServiceFixture extends SQS with SierraAdapterHelpers {
  def withWorkerService[R](
    queue: Queue,
    dao: SierraDao,
    store: SierraItemStore,
    messageSender: MemoryMessageSender)(testWith: TestWith[SierraItemsToDynamoWorkerService[String], R]): R =
    withMetricsSender() { metricsSender =>
      withWorkerService(queue, dao, store, messageSender, metricsSender) {
        workerService =>
          testWith(workerService)
      }
    }

  def withWorkerService[R](queue: Queue,
                           dao: SierraDao,
                           store: SierraItemStore,
                           messageSender: MemoryMessageSender,
                           metricsSender: MetricsSender)(
    testWith: TestWith[SierraItemsToDynamoWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue, metricsSender) { sqsStream =>
        val vhs = createItemVhs(dao, store)
        val vhsInserter = new VHSInserter(vhs)

        val workerService = new SierraItemsToDynamoWorkerService(
          sqsStream = sqsStream,
          vhsInserter = vhsInserter,
          messageSender = messageSender
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
