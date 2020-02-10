package uk.ac.wellcome.calm_adapter

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.akka.fixtures.Akka

class CalmHttpClientTest
    extends FunSpec
    with Matchers
    with Akka
    with ScalaFutures {

  val minBackoff = 0.1 seconds
  val maxBackoff = 0 seconds
  val randomFactor = 0.0
  val maxRestarts = 2
  val protocol = HttpProtocols.`HTTP/1.0`

  val request = HttpRequest(uri = "http://calm.api")
  val response200 = HttpResponse(200, Nil, ":)", protocol)
  val response500 = HttpResponse(500, Nil, ":(", protocol)
  val response408 = HttpResponse(408, Nil, ":/", protocol)

  it("returns first API response when the status is OK") {
    val responses = List(response200, response500, response500, response500)
    withHttpClient(responses) { httpClient =>
      whenReady(httpClient(request)) { response =>
        response shouldBe response200
      }
    }
  }

  it("retries calling the API when status is not OK") {
    val responses = List(response500, response408, response200, response500)
    withHttpClient(responses) { httpClient =>
      whenReady(httpClient(request)) { response =>
        response shouldBe response200
      }
    }
  }

  it("throws an error if max retries exceeded") {
    val responses = List(response500, response500, response500, response500)
    withHttpClient(responses) { httpClient =>
      whenReady(httpClient(request).failed) { err =>
        err.getMessage shouldBe "Max retries attempted when calling Calm API"
      }
    }
  }

  def withHttpClient[R](responses: List[HttpResponse])(
    testWith: TestWith[CalmHttpTestClient, R]) =
    withMaterializer { implicit materializer =>
      testWith(new CalmHttpTestClient(responses))
    }

  class CalmHttpTestClient(var responses: List[HttpResponse])(
    implicit materializer: ActorMaterializer)
      extends CalmHttpClientWithBackoff(
        minBackoff,
        maxBackoff,
        randomFactor,
        maxRestarts) {
    var requests: List[HttpRequest] = Nil
    def singleRequest(request: HttpRequest): Future[HttpResponse] = {
      val response = responses.headOption
      responses = responses.drop(1)
      requests = requests :+ request
      response
        .map(Future.successful(_))
        .getOrElse(Future.failed(new Exception("Ooops")))
    }
  }
}
