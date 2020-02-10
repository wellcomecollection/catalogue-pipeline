package uk.ac.wellcome.calm_adapter

import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling.Unmarshal

trait CalmRetriever {

  def apply(query: CalmQuery): Future[List[CalmRecord]]
}

/** Retrieves a list of CALM records from the API given some query.
  *
  * To retrieve records from the CALM API multiple requests are needed, each
  * containing a SOAP document with the request data. An initial request is
  * first made which searches for the number of hits. The API is stateful, with
  * the response including a session cookie which is used on subsequenet
  * requests, one for each of the indvidual records.
  */
class HttpCalmRetriever(url: String, username: String, password: String)(
  implicit
  ec: ExecutionContext,
  materializer: ActorMaterializer,
  httpClient: CalmHttpClient)
    extends CalmRetriever {

  type Result[T] = Either[Throwable, T]

  def apply(query: CalmQuery): Future[List[CalmRecord]] =
    callApi(CalmSearchRequest(query), searchResponseParser)
      .flatMap {
        case CalmSession(numHits, cookie) =>
          runSequentially(
            0 until numHits,
            (pos: Int) =>
              callApi(
                CalmSummaryRequest(pos),
                summaryResponseParser,
                Some(cookie))
          )
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
      HttpRequest(uri = url)
        .withEntity(ContentTypes.`text/xml(UTF-8)`, xmlRequest.xml.toString)
        .addCredentials(BasicHttpCredentials(username, password))
        .addHeader(
          RawHeader("SOAPAction", "http://ds.co.uk/cs/webservices/Search")
        )
    cookie match {
      case Some(cookie) => request.addHeader(cookie)
      case None         => request
    }
  }

  trait CalmResponseParser[T] {
    def apply(resp: HttpResponse): Future[T] =
      Unmarshal(resp.entity)
        .to[String]
        .flatMap { str =>
          Future.fromTry(parseXml(resp, str).flatMap(_.parse).toTry)
        }

    def parseXml(resp: HttpResponse, str: String): Result[CalmXmlResponse[T]]
  }

  val searchResponseParser = new CalmResponseParser[CalmSession] {
    def parseXml(resp: HttpResponse,
                 str: String): Result[CalmXmlResponse[CalmSession]] =
      CalmSearchResponse(str, parseCookie(resp))
  }

  val summaryResponseParser = new CalmResponseParser[CalmRecord] {
    def parseXml(resp: HttpResponse,
                 str: String): Result[CalmXmlResponse[CalmRecord]] =
      CalmSummaryResponse(str, parseTimestamp(resp))
  }

  def parseCookie(resp: HttpResponse): Cookie =
    resp.headers
      .collect {
        case `Set-Cookie`(cookie) => Cookie(cookie.pair)
      }
      .headOption
      .getOrElse(
        throw new Exception("Session cookie not found in CALM response"))

  def parseTimestamp(resp: HttpResponse): Instant =
    resp.headers
      .collect {
        case `Date`(dateTime) => Instant.ofEpochMilli(dateTime.clicks)
      }
      .headOption
      .getOrElse(throw new Exception("Timestamp not found in CALM response"))

  /** Utility method to apply a function returning a Future on a sequence of
    *  inputs, waiting for the result of one Future before proceeding to dispatch
    *  the next. */
  def runSequentially[I, O](inputs: Seq[I],
                            f: I => Future[O]): Future[List[O]] =
    inputs.foldLeft(Future.successful[List[O]](Nil)) { (future, input) =>
      future.flatMap { results =>
        f(input).map(results :+ _)
      }
    }
}
