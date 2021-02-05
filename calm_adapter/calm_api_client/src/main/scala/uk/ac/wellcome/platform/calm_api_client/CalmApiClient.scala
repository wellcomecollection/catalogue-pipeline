package uk.ac.wellcome.platform.calm_api_client

import akka.http.scaladsl.model.{
  ContentTypes,
  HttpMethods,
  HttpRequest,
  StatusCodes
}
import akka.http.scaladsl.model.headers.{
  BasicHttpCredentials,
  Cookie,
  RawHeader
}
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}

class CalmApiClient(
  url: String,
  username: String,
  password: String,
)(implicit ec: ExecutionContext,
  mat: Materializer,
  httpClient: CalmHttpClient) {
  import CalmHttpResponseParser._

  def search(query: CalmQuery,
             cookie: Option[Cookie] = None): Future[CalmSession] =
    request(CalmSearchRequest(query), cookie)

  // We defer resolution of the summary parser so consumers can suppress fields
  def summary(pos: Int, cookie: Option[Cookie] = None)(
    implicit p: CalmHttpResponseParser[CalmSummaryRequest])
    : Future[CalmRecord] =
    request(CalmSummaryRequest(pos), cookie)

  private def request[Request <: CalmXmlRequest](request: Request,
                                                 cookie: Option[Cookie])(
    implicit parse: CalmHttpResponseParser[Request]): Future[Request#Response] =
    httpClient(createHttpRequest(request, cookie))
      .flatMap { resp =>
        resp.status match {
          case StatusCodes.OK => parse(resp)
          case status =>
            Future.failed(
              new Exception(s"Unexpected status from CALM API: $status"))
        }
      }

  private def createHttpRequest(xmlRequest: CalmXmlRequest,
                                cookie: Option[Cookie]): HttpRequest = {
    val request =
      HttpRequest(uri = url, method = HttpMethods.POST)
        .withEntity(ContentTypes.`text/xml(UTF-8)`, xmlRequest.xml.toString)
        .addCredentials(BasicHttpCredentials(username, password))
        .addHeader(
          RawHeader(
            "SOAPAction",
            s"http://ds.co.uk/cs/webservices/${xmlRequest.action}")
        )
    cookie match {
      case Some(cookie) => request.addHeader(cookie)
      case None         => request
    }
  }

}
