package uk.ac.wellcome.calm_adapter

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.xml.XML
import java.time.{Instant, LocalDate}
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith

class CalmRetrieverTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with Akka {

  val url = "calm.api"
  val username = "calm-user"
  val password = "calm-password"
  val cookie = ("cookie-name", "cookie-value")
  val protocol = HttpProtocols.`HTTP/1.0`
  val query = CalmQuery.ModifiedDate(LocalDate.of(2000, 1, 1))
  val retrievedAt = Instant.ofEpochSecond(123456)

  it("generates a list of CALM records from the API") {
    val responses = List(
      searchResponse(2),
      summaryResponse(
        List("RecordID" -> "1", "keyA" -> "valueA", "keyB" -> "valueB")),
      summaryResponse(List("RecordID" -> "2", "keyC" -> "valueC"))
    )
    withMaterializer { implicit materializer =>
      withCalmRetriever(responses) {
        case (calmRetriever, _) =>
          whenReady(calmRetriever(query).runWith(Sink.seq)) { records =>
            records shouldBe List(
              CalmRecord(
                "1",
                Map(
                  "RecordID" -> List("1"),
                  "keyA" -> List("valueA"),
                  "keyB" -> List("valueB")),
                retrievedAt),
              CalmRecord(
                "2",
                Map("RecordID" -> List("2"), "keyC" -> List("valueC")),
                retrievedAt),
            )
          }
      }
    }
  }

  it("uses the credentials for all API requests") {
    val responses = List(
      searchResponse(2),
      summaryResponse(List("RecordID" -> "1")),
      summaryResponse(List("RecordID" -> "2"))
    )
    withMaterializer { implicit materializer =>
      withCalmRetriever(responses) {
        case (calmRetriever, httpClient) =>
          whenReady(calmRetriever(query).runWith(Sink.seq)) { records =>
            val credentialsHeaders = httpClient.requests.map { request =>
              request.headers.collect { case auth: Authorization => auth }
            }
            credentialsHeaders shouldBe List(
              List(Authorization(BasicHttpCredentials(username, password))),
              List(Authorization(BasicHttpCredentials(username, password))),
              List(Authorization(BasicHttpCredentials(username, password))),
            )
          }
      }
    }
  }

  it("sets the 'SOAPAction' header for all API requests") {
    val responses = List(
      searchResponse(2),
      summaryResponse(List("RecordID" -> "1")),
      summaryResponse(List("RecordID" -> "2"))
    )
    withMaterializer { implicit materializer =>
      withCalmRetriever(responses) {
        case (calmRetriever, httpClient) =>
          whenReady(calmRetriever(query).runWith(Sink.seq)) { records =>
            val soapHeaders = httpClient.requests.map { request =>
              request.headers.collect { case header: RawHeader => header }
            }
            soapHeaders shouldBe List(
              List(
                RawHeader(
                  "SOAPAction",
                  "http://ds.co.uk/cs/webservices/Search")),
              List(
                RawHeader(
                  "SOAPAction",
                  "http://ds.co.uk/cs/webservices/SummaryHeader")),
              List(
                RawHeader(
                  "SOAPAction",
                  "http://ds.co.uk/cs/webservices/SummaryHeader")),
            )
          }
      }
    }
  }

  it("uses the cookie from the first response for subsequent API requests") {
    val responses = List(
      searchResponse(2),
      summaryResponse(List("RecordID" -> "1")),
      summaryResponse(List("RecordID" -> "2"))
    )
    withMaterializer { implicit materializer =>
      withCalmRetriever(responses) {
        case (calmRetriever, httpClient) =>
          whenReady(calmRetriever(query).runWith(Sink.seq)) { records =>
            val cookieHeaders = httpClient.requests.map { request =>
              request.headers.collect { case cookie: Cookie => cookie }
            }
            cookieHeaders shouldBe List(
              Nil,
              List(Cookie(cookie)),
              List(Cookie(cookie)),
            )
          }
      }
    }
  }

  it("uses num hits from the first response for subsequent API requests") {
    val responses = List(
      searchResponse(3),
      summaryResponse(List("RecordID" -> "1")),
      summaryResponse(List("RecordID" -> "2")),
      summaryResponse(List("RecordID" -> "3"))
    )
    withMaterializer { implicit materializer =>
      withCalmRetriever(responses) {
        case (calmRetriever, httpClient) =>
          whenReady(calmRetriever(query).runWith(Sink.seq)) { records =>
            val hitPositions = httpClient.requests.map { request =>
              val body = Await
                .result(request.entity.toStrict(0 seconds), 0 seconds)
                .data
                .decodeString("utf-8")
              (XML.loadString(body) \\ "HitLstPos").headOption.map(_.text)
            }
            hitPositions shouldBe List(
              None,
              Some("0"),
              Some("1"),
              Some("2"),
            )
          }
      }
    }
  }

  it("fails if there is no session cookie in the inital response") {
    val responses = List(
      searchResponse(1, None),
      summaryResponse(List("RecordID" -> "1"))
    )
    withMaterializer { implicit materializer =>
      withCalmRetriever(responses) {
        case (calmRetriever, _) =>
          whenReady(calmRetriever(query).runWith(Sink.seq).failed) { failure =>
            failure.getMessage shouldBe "Session cookie not found in CALM response"
          }
      }
    }
  }

  it("fails if error response from API") {
    val responses = List(
      searchResponse(2),
      summaryResponse(List("RecordID" -> "1")),
      HttpResponse(500, Nil, "Error", protocol)
    )
    withMaterializer { implicit materializer =>
      withCalmRetriever(responses) {
        case (calmRetriever, _) =>
          whenReady(calmRetriever(query).runWith(Sink.seq).failed) { failure =>
            failure.getMessage shouldBe "Unexpected status from CALM API: 500 Internal Server Error"
          }
      }
    }
  }

  it("fails if there is no RecordID in the response") {
    val responses = List(
      searchResponse(1),
      summaryResponse(Nil)
    )
    withMaterializer { implicit materializer =>
      withCalmRetriever(responses) {
        case (calmRetriever, _) =>
          whenReady(calmRetriever(query).runWith(Sink.seq).failed) { failure =>
            failure.getMessage shouldBe "RecordID not found"
          }
      }
    }
  }

  def searchResponse(n: Int,
                     cookiePair: Option[(String, String)] = Some(cookie)) =
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

  def summaryResponse(data: List[(String, String)],
                      timestamp: Option[Instant] = Some(retrievedAt)) =
    HttpResponse(
      200,
      timestamp.map(ts => Date(DateTime(ts.toEpochMilli))).toList,
      <soap:Envelope
          xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:xsd="http://www.w3.org/2001/XMLSchema">
        <soap:Body>
          <SummaryHeaderResponse xmlns="http://ds.co.uk/cs/webservices/">
            <SummaryHeaderResult>
              <SummaryList>
                <Summary>
                  {data.map { case (key, value) =>
                    XML.loadString(s"<$key>$value</$key>") }}
                </Summary>
              </SummaryList>
            </SummaryHeaderResult>
          </SummaryHeaderResponse>
        </soap:Body>
      </soap:Envelope>.toString,
      protocol
    )

  def withCalmRetriever[R](responses: List[HttpResponse])(
    testWith: TestWith[(CalmRetriever, CalmHttpTestClient), R])(
    implicit materializer: ActorMaterializer) = {
    implicit val httpClient = new CalmHttpTestClient(responses)
    testWith((new HttpCalmRetriever(url, username, password), httpClient))
  }

  class CalmHttpTestClient(var responses: List[HttpResponse])
      extends CalmHttpClient {
    var requests: List[HttpRequest] = Nil
    def apply(request: HttpRequest): Future[HttpResponse] = {
      val response = responses.headOption
      responses = responses.drop(1)
      requests = requests :+ request
      response
        .map(Future.successful(_))
        .getOrElse(Future.failed(new Exception("Request failed")))
    }
  }
}
