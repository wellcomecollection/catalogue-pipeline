package uk.ac.wellcome.platform.sierra_bib_merger.services

import org.mockito.Mockito.{never, verify}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.monitoring.Metrics
import uk.ac.wellcome.platform.sierra_bib_merger.fixtures.WorkerServiceFixture
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraTransformable
}

import scala.concurrent.Future

class SierraBibMergerWorkerServiceTest
    extends AnyFunSpec
    with MockitoSugar
    with Matchers
    with SierraGenerators
    with WorkerServiceFixture
    with Eventually
    with IntegrationPatience {

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
    testWith: TestWith[(Metrics[Future, StandardUnit], QueuePair), R]): R = {
    val metricsSender = mock[Metrics[Future, StandardUnit]]
    withLocalSqsQueuePair() {
      case queuePair @ QueuePair(queue, _) =>
        val store = createStore[SierraTransformable]()
        withWorkerService(store, queue) { _ =>
          testWith((metricsSender, queuePair))
        }
    }
  }
}
