package weco.catalogue.sierra_holdings_linker.services

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.sierra_holdings_linker.fixtures.WorkerServiceFixture

class SierraHoldingsLinkerWorkerServiceTest
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
