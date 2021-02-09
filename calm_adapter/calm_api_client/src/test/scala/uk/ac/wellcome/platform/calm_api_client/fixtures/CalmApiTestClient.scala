package uk.ac.wellcome.platform.calm_api_client.fixtures

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.calm_api_client.{
  CalmApiClient,
  CalmHttpClientWithBackoff
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait CalmApiTestClient extends Akka {
  val url = "calm.api"
  val username = "calm-user"
  val password = "calm-password"

  val minBackoff = 0.1 seconds
  val maxBackoff = 0 seconds
  val randomFactor = 0.0
  val maxRestarts = 2

  def withCalmClients[R](responses: List[HttpResponse])(
    testWith: TestWith[(CalmApiClient, TestHttpClient), R]): R =
    withHttpClient(responses) { implicit httpClient =>
      withMaterializer { implicit mat =>
        implicit val ec = mat.executionContext
        testWith((new CalmApiClient(url, username, password), httpClient))
      }
    }

  def withHttpClient[R](responses: List[HttpResponse])(
    testWith: TestWith[TestHttpClient, R]): R =
    withMaterializer { implicit mat =>
      testWith(new TestHttpClient(responses))
    }

  class TestHttpClient(var responses: List[HttpResponse])(
    implicit mat: Materializer)
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
        .map(Future.successful)
        .getOrElse(Future.failed(new Exception("Request failed")))
    }
  }

}
