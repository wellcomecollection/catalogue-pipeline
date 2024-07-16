package weco.pipeline.calm_api_client

import org.apache.pekko.stream.RestartSettings
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pekko.fixtures.Pekko

import scala.concurrent.duration._
import scala.concurrent.Future

class RetryFutureTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with Pekko
    with IntegrationPatience {

  val maxRetries = 5

  implicit val restartSettings: RestartSettings = RestartSettings(
    minBackoff = 50 milliseconds,
    maxBackoff = 100 milliseconds,
    randomFactor = 0.0
  ).withMaxRestarts(maxRetries, maxRetries * 100 milliseconds)

  it("returns the future if it is initially a success") {
    withMaterializer {
      implicit mat =>
        whenReady(RetryFuture(Future.successful("success"))) {
          result =>
            result shouldBe "success"
        }
    }
  }
  it("returns the first success if the future initially fails") {
    withMaterializer {
      implicit mat =>
        val doAttempt = succeedAfterTries(3)
        whenReady(RetryFuture(doAttempt())) {
          result =>
            result shouldBe 3
        }
    }
  }

  it("throws an error if the configured max retries are exceeded") {
    withMaterializer {
      implicit mat =>
        val doAttempt = succeedAfterTries(maxRetries + 1)
        whenReady(RetryFuture(doAttempt()).failed) {
          e =>
            e shouldBe an[Exception]
        }
    }
  }

  def succeedAfterTries(n: Int): () => Future[Int] = {
    var tries = 0
    () =>
      if (tries >= n) {
        Future.successful(tries)
      } else {
        tries += 1
        Future.failed(new Exception("failed"))
      }
  }
}
