package uk.ac.wellcome.platform.sierra_bib_merger.services

import com.amazonaws.services.cloudwatch.model.StandardUnit
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.platform.sierra_bib_merger.fixtures.WorkerServiceFixture

class SierraBibMergerWorkerServiceTest
    extends FunSpec
    with ScalaFutures
    with Matchers
    with IntegrationPatience
    with SierraGenerators
    with WorkerServiceFixture
    with Eventually {

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
          metrics.incrementedCounts should not contain "SierraBibMergerUpdaterService_ProcessMessage_failure"
        }
    }
  }

  private def withWorkerServiceFixtures[R](
    testWith: TestWith[(MemoryMetrics[StandardUnit], QueuePair), R]): R =
    withLocalSqsQueueAndDlq {
      case queuePair @ QueuePair(queue, _) =>
        val metrics = new MemoryMetrics[StandardUnit]()
        val vhs = createVhs()
        val messageSender = new MemoryMessageSender
        withWorkerService(vhs, queue, messageSender, metrics) { _ =>
          testWith((metrics, queuePair))
        }
    }
}
