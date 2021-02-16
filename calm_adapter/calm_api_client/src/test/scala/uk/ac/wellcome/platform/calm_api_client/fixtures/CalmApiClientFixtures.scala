package uk.ac.wellcome.platform.calm_api_client.fixtures

import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.calm_api_client._

import scala.concurrent.{ExecutionContext, Future}
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
    handleSummary: Int => CalmRecord = _ => throw new NotImplementedError
  )(testWith: TestWith[TestCalmApiClient, R]): R =
    withMaterializer { mat =>
      implicit val ec: ExecutionContext = mat.executionContext
      testWith(new TestCalmApiClient(handleSearch, handleSummary))
    }

  trait TestHttpClient extends HttpClient {
    val responses: Iterator[HttpResponse]
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
        url,
        username,
        password,
        minBackoff,
        maxBackoff,
        randomFactor,
        maxRestarts)
      with TestHttpClient {
    val responses = responseList.iterator
  }

  class TestCalmApiClient(
    handleSearch: CalmQuery => CalmSession,
    handleSummary: Int => CalmRecord
  )(implicit ec: ExecutionContext)
      extends CalmApiClient {
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
        }
      }
    }
  }

}
