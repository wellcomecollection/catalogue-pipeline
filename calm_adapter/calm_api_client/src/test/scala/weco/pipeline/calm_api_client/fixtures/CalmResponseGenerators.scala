package weco.pipeline.calm_api_client.fixtures

import java.time.Instant

import org.apache.pekko.http.scaladsl.model.{
  DateTime,
  HttpProtocols,
  HttpResponse
}
import org.apache.pekko.http.scaladsl.model.headers.{
  `Set-Cookie`,
  Date,
  HttpCookie
}

import scala.xml.XML

trait CalmResponseGenerators {

  val cookie = ("cookie-name", "cookie-value")
  val protocol = HttpProtocols.`HTTP/1.0`
  val retrievedAt = Instant.ofEpochSecond(123456)

  def searchResponse(
    n: Int,
    cookiePair: Option[(String, String)] = Some(cookie)
  ): HttpResponse =
    HttpResponse(
      200,
      cookiePair.map {
        case (name, value) => `Set-Cookie`(HttpCookie(name, value))
      }.toList,
      <soap:Envelope
      xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xmlns:xsd="http://www.w3.org/2001/XMLSchema">
        <soap:Body>
          <SearchResponse xmlns="http://ds.co.uk/cs/webservices/">
            <SearchResult>{n}</SearchResult>
          </SearchResponse>
        </soap:Body>
      </soap:Envelope>.toString,
      protocol
    )

  def summaryResponse(data: (String, String)*): HttpResponse =
    HttpResponse(
      200,
      List(Date(DateTime(retrievedAt.toEpochMilli))),
      <soap:Envelope
      xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xmlns:xsd="http://www.w3.org/2001/XMLSchema">
        <soap:Body>
          <SummaryHeaderResponse xmlns="http://ds.co.uk/cs/webservices/">
            <SummaryHeaderResult>
              <SummaryList>
                <Summary>
                  {
        data.map {
          case (key, value) =>
            XML.loadString(s"<$key>$value</$key>")
        }
      }
                </Summary>
              </SummaryList>
            </SummaryHeaderResult>
          </SummaryHeaderResponse>
        </soap:Body>
      </soap:Envelope>.toString,
      protocol
    )

  def abandonResponse: HttpResponse =
    HttpResponse(
      200,
      Nil,
      <soap:Envelope
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xmlns:xsd="http://www.w3.org/2001/XMLSchema"
      xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Body>
          <AbandonResponse xmlns="http://ds.co.uk/cs/webservices/" />
        </soap:Body>
      </soap:Envelope>.toString,
      protocol
    )
}
