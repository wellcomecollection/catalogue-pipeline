package weco.catalogue.sierra_holdings_merger.services

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.sierra_holdings_merger.fixtures.WorkerServiceFixture

class SierraHoldingsMergerWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WorkerServiceFixture {

  it("works") {
    withLocalSqsQueue() { queue =>
      withWorkerService(queue) { workerService =>
        val future = workerService.process(
          createNotificationMessageWith(body = "hello world")
        )

        whenReady(future) {
          _ shouldBe (())
        }
      }
    }
  }
}
