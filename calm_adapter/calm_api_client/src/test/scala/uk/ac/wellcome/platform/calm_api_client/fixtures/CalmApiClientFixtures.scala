package uk.ac.wellcome.platform.calm_api_client.fixtures

import akka.Done
import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.calm_api_client._
import weco.catalogue.source_model.calm.CalmRecord
import weco.http.client.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

trait CalmApiClientFixtures extends Akka {
  val url = "calm.api"
  val username = "calm-user"
  val password = "calm-password"

  val minBackoff = 0.1 seconds
  val maxBackoff = 0 seconds
  val randomFactor = 0.0
  val maxRestarts = 2

  def withTestHttpCalmApiClient[R](responses: List[HttpResponse])(
    testWith: TestWith[TestHttpCalmApiClient, R]): R =
    withMaterializer { implicit mat =>
      val client = new TestHttpCalmApiClient(responses)
      testWith(client)
    }

  def withTestCalmApiClient[R](
    handleSearch: CalmQuery => CalmSession = _ => throw new NotImplementedError,
    handleSummary: Int => CalmRecord = _ => throw new NotImplementedError,
    handleAbandon: Cookie => Done = _ => throw new NotImplementedError,
  )(testWith: TestWith[TestCalmApiClient, R]): R =
    withMaterializer { mat =>
      testWith(
        new TestCalmApiClient(handleSearch, handleSummary, handleAbandon))
    }

  class TestHttpClient(responses: Iterator[HttpResponse])(
    implicit val ec: ExecutionContext)
      extends HttpClient {
    final var requests: List[HttpRequest] = Nil

    def singleRequest(request: HttpRequest): Future[HttpResponse] = {
      requests = requests :+ request
      if (responses.hasNext) {
        Future.successful(responses.next())
      } else {
        Future.failed(new Exception("Request failed"))
      }
    }
  }

  class TestHttpCalmApiClient(responseList: List[HttpResponse])(
    implicit mat: Materializer)
      extends HttpCalmApiClient(
        client = new TestHttpClient(responseList.toIterator),
        url,
        username,
        password,
        minBackoff,
        maxBackoff,
        randomFactor,
        maxRestarts) {
    def requests: List[HttpRequest] =
      client.asInstanceOf[TestHttpClient].requests
  }

  class TestCalmApiClient(
    handleSearch: CalmQuery => CalmSession,
    handleSummary: Int => CalmRecord,
    handleAbandon: Cookie => Done
  ) extends CalmApiClient {
    var requests: List[(CalmXmlRequest, Option[Cookie])] = Nil

    def request[Request <: CalmXmlRequest: CalmHttpResponseParser](
      request: Request,
      cookie: Option[Cookie]
    ): Future[Request#Response] = {
      requests = requests :+ ((request, cookie))
      Future {
        request match {
          case CalmSearchRequest(query, _) =>
            handleSearch(query).asInstanceOf[Request#Response]
          case CalmSummaryRequest(pos, _) =>
            handleSummary(pos).asInstanceOf[Request#Response]
          case CalmAbandonRequest =>
            handleAbandon(cookie.get).asInstanceOf[Request#Response]
        }
      }
    }
  }

}
