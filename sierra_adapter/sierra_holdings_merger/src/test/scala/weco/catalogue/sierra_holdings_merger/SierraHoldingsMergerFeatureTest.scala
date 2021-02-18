package weco.catalogue.sierra_holdings_merger

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.sierra_holdings_merger.fixtures.WorkerServiceFixture

class SierraHoldingsMergerFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with WorkerServiceFixture {

  it("works") {
    withLocalSqsQueue() { queue =>
      withWorkerService(queue) { _ =>
        sendNotificationToSQS(queue, body = "hello world")

        eventually {
          assertQueueEmpty(queue)
        }
      }
    }
  }
}
