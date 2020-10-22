package uk.ac.wellcome.platform.sierra_bib_merger.services

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.platform.sierra_bib_merger.fixtures.WorkerServiceFixture
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraTransformable
}

class SierraBibMergerWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with SierraGenerators
    with WorkerServiceFixture
    with Eventually
    with IntegrationPatience {

  it(
    "records a failure if the message on the queue does not represent a SierraRecord") {
    withWorkerServiceFixtures {
      case (metrics, QueuePair(queue, dlq)) =>
        sendNotificationToSQS(
          queue = queue,
          body = "null"
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 1)

          metrics.incrementedCounts should contain(
            "SierraBibMergerWorkerService_ProcessMessage_recognisedFailure")
        }
    }
  }

  private def withWorkerServiceFixtures[R](
    testWith: TestWith[(MemoryMetrics, QueuePair), R]): R = {
    val metrics = new MemoryMetrics()
    withLocalSqsQueuePair() {
      case queuePair @ QueuePair(queue, _) =>
        val store = createStore[SierraTransformable]()
        withWorkerService(store, queue, metrics) { _ =>
          testWith((metrics, queuePair))
        }
    }
  }
}
