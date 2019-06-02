package uk.ac.wellcome.platform.sierra_bib_merger.services

import org.mockito.Mockito.{never, verify}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.platform.sierra_bib_merger.fixtures.WorkerServiceFixture

class SierraBibMergerWorkerServiceTest
    extends FunSpec
    with MockitoSugar
    with ScalaFutures
    with Matchers
    with SQS
    with IntegrationPatience
    with SierraGenerators
    with WorkerServiceFixture {

  it(
    "records a failure if the message on the queue does not represent a SierraRecord") {
    withWorkerServiceFixtures {
      case (metricsSender, QueuePair(queue, dlq)) =>
        sendNotificationToSQS(
          queue = queue,
          body = "null"
        )

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 1)
          verify(metricsSender, never()).incrementCount(
            "SierraBibMergerUpdaterService_ProcessMessage_failure")
        }
    }
  }

  private def withWorkerServiceFixtures[R](
    testWith: TestWith[(MetricsSender, QueuePair), R]): R =
    withMockMetricsSender { metricsSender =>
      withLocalSqsQueueAndDlq {
        case queuePair @ QueuePair(queue, _) =>
          val vhs = createVhs()
          val messageSender = new MemoryMessageSender
          withWorkerService(vhs, queue, messageSender) { _ =>
            testWith((metricsSender, queuePair))
          }
      }
    }
}
