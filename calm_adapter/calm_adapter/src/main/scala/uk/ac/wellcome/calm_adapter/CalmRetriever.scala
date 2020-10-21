package uk.ac.wellcome.calm_adapter

import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant
import grizzled.slf4j.Logging

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal

trait CalmRetriever {

  def apply(query: CalmQuery): Source[CalmRecord, NotUsed]
}

/** Retrieves a list of CALM records from the API given some query.
  *
  * To retrieve records from the CALM API multiple requests are needed, each
  * containing a SOAP document with the request data. An initial request is
  * first made which searches for the number of hits. The API is stateful, with
  * the response including a session cookie which is used on subsequent
  * requests, one for each of the individual records.
  */
class HttpCalmRetriever(url: String,
                        username: String,
                        password: String,
                        concurrentHttpConnections: Int = 2,
                        suppressedFields: Set[String] = Set.empty)(
  implicit
  ec: ExecutionContext,
  materializer: Materializer,
  httpClient: CalmHttpClient)
    extends CalmRetriever
    with Logging {

  type Result[T] = Either[Throwable, T]

  def apply(query: CalmQuery): Source[CalmRecord, NotUsed] =
    Source
      .future(callApi(CalmSearchRequest(query), searchResponseParser))
      .mapConcat {
        case CalmSession(numHits, cookie) =>
          info(s"Received $numHits records for query: ${query.queryExpression}")
          (0 until numHits).map(pos => (pos, cookie))
      }
      .mapAsync(concurrentHttpConnections) {
        case (pos, cookie) =>
          info(s"Querying record $pos for query: ${query.queryExpression}")
          callApi(CalmSummaryRequest(pos), summaryResponseParser, Some(cookie))
      }

  def callApi[T](xmlRequest: CalmXmlRequest,
                 parser: CalmResponseParser[T],
                 cookie: Option[Cookie] = None): Future[T] =
    httpClient(calmRequest(xmlRequest, cookie))
      .flatMap { resp =>
        resp.status match {
          case StatusCodes.OK => parser(resp)
          case status =>
            Future.failed(
              new Exception(s"Unexpected status from CALM API: $status"))
        }
      }

  def calmRequest(xmlRequest: CalmXmlRequest,
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

  trait CalmResponseParser[T] {
    def apply(resp: HttpResponse): Future[T] =
      Unmarshal(resp.entity)
        .to[Array[Byte]]
        .flatMap { bytes =>
          Future.fromTry(parseXml(resp, bytes).flatMap(_.parse).toTry)
        }

    def parseXml(resp: HttpResponse,
                 bytes: Array[Byte]): Result[CalmXmlResponse[T]]
  }

  val searchResponseParser = new CalmResponseParser[CalmSession] {
    def parseXml(resp: HttpResponse,
                 bytes: Array[Byte]): Result[CalmXmlResponse[CalmSession]] =
      CalmSearchResponse(bytes, parseCookie(resp))
  }

  val summaryResponseParser = new CalmResponseParser[CalmRecord] {
    def parseXml(resp: HttpResponse,
                 bytes: Array[Byte]): Result[CalmXmlResponse[CalmRecord]] =
      CalmSummaryResponse(bytes, parseTimestamp(resp), suppressedFields)
  }

  def parseCookie(resp: HttpResponse): Cookie =
    resp.headers
      .collectFirst {
        case `Set-Cookie`(cookie) => Cookie(cookie.pair)
      }
      .getOrElse(
        throw new Exception("Session cookie not found in CALM response"))

  def parseTimestamp(resp: HttpResponse): Instant =
    resp.headers
      .collectFirst {
        case `Date`(dateTime) => Instant.ofEpochMilli(dateTime.clicks)
      }
      .getOrElse(throw new Exception("Timestamp not found in CALM response"))
}
