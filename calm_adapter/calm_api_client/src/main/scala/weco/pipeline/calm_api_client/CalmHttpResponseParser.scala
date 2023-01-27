package weco.pipeline.calm_api_client

import java.time.Instant

import akka.http.scaladsl.model.{HttpResponse, ResponseEntity}
import akka.http.scaladsl.model.headers.{`Set-Cookie`, Cookie, Date}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.util.ByteString
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContext, Future}

trait CalmHttpResponseParser[Request <: CalmXmlRequest] extends Logging {
  type Result[U] = Either[Throwable, U]

  def apply(resp: HttpResponse)(implicit
    mat: Materializer,
    ec: ExecutionContext
  ): Future[Request#Response] =
    Unmarshal(resp.entity)
      .to[Array[Byte]]
      .flatMap { bytes =>
        Future.fromTry(parseXml(resp, bytes).flatMap(_.parse).toTry)
      }
      .recoverWith { case parseException =>
        CalmHttpResponseParser.responseText(resp.entity).map { responseString =>
          error(s"Error while parsing response: $responseString")
          throw parseException
        }
      }

  def parseXml(
    resp: HttpResponse,
    bytes: Array[Byte]
  ): Result[CalmXmlResponse[Request#Response]]
}

object CalmHttpResponseParser {

  implicit val searchResponseParser: CalmHttpResponseParser[CalmSearchRequest] =
    (resp: HttpResponse, bytes: Array[Byte]) =>
      CalmSearchResponse(bytes, parseCookie(resp))

  implicit val abandonResponseParser
    : CalmHttpResponseParser[CalmAbandonRequest.type] =
    (_, bytes: Array[Byte]) => CalmAbandonResponse(bytes)

  def createSummaryResponseParser(
    suppressedFields: Set[String]
  ): CalmHttpResponseParser[CalmSummaryRequest] =
    (resp: HttpResponse, bytes: Array[Byte]) =>
      CalmSummaryResponse(bytes, parseTimestamp(resp), suppressedFields)

  private def parseCookie(resp: HttpResponse): Cookie =
    resp.headers
      .collectFirst { case `Set-Cookie`(cookie) =>
        Cookie(cookie.pair)
      }
      .getOrElse(
        throw new Exception("Session cookie not found in CALM response")
      )

  private def parseTimestamp(resp: HttpResponse): Instant =
    resp.headers
      .collectFirst { case `Date`(dateTime) =>
        Instant.ofEpochMilli(dateTime.clicks)
      }
      .getOrElse(throw new Exception("Timestamp not found in CALM response"))

  private def responseText(
    entity: ResponseEntity
  )(implicit mat: Materializer, ec: ExecutionContext): Future[String] =
    Unmarshal(entity)
      .to[ByteString]
      .map(_.utf8String)
}
