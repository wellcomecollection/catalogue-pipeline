package uk.ac.wellcome.calm_adapter

import java.time.LocalDate

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.Materializer
import akka.stream.scaladsl._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.calm_api_client._
import uk.ac.wellcome.platform.calm_api_client.fixtures.{
  CalmHttpTestClient,
  CalmResponseGenerators
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.xml.XML

class CalmRetrieverTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with Akka
    with CalmResponseGenerators {

  val url = "calm.api"
  val username = "calm-user"
  val password = "calm-password"
  val query = CalmQuery.ModifiedDate(LocalDate.of(2000, 1, 1))

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
          whenReady(calmRetriever(query).runWith(Sink.seq[CalmRecord])) {
            records =>
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

  it("uses the cookie from the first response for subsequent API requests") {
    val responses = List(
      searchResponse(2),
      summaryResponse(List("RecordID" -> "1")),
      summaryResponse(List("RecordID" -> "2"))
    )
    withMaterializer { implicit materializer =>
      withCalmRetriever(responses) {
        case (calmRetriever, httpClient) =>
          whenReady(calmRetriever(query).runWith(Sink.seq[CalmRecord])) { _ =>
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
          whenReady(calmRetriever(query).runWith(Sink.seq[CalmRecord])) { _ =>
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

  def withCalmRetriever[R](responses: List[HttpResponse])(
    testWith: TestWith[(CalmRetriever, CalmHttpTestClient), R])(
    implicit mat: Materializer): R = {
    implicit val httpClient = new CalmHttpTestClient(responses)
    val apiClient = new CalmApiClient(url, username, password)
    testWith((new ApiCalmRetriever(apiClient), httpClient))
  }
}
