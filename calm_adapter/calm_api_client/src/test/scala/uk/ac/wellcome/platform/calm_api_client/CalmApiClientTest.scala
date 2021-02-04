package uk.ac.wellcome.platform.calm_api_client

import java.time.LocalDate

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.{
  Authorization,
  BasicHttpCredentials,
  Cookie,
  RawHeader
}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.calm_api_client.fixtures.{
  CalmHttpTestClient,
  CalmResponseGenerators
}

import scala.concurrent.ExecutionContext.Implicits.global

class CalmApiClientTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with CalmResponseGenerators
    with Akka {

  val url = "calm.api"
  val username = "calm-user"
  val password = "calm-password"
  val query = CalmQuery.ModifiedDate(LocalDate.of(2000, 1, 1))
  val suppressedField = "Secret" -> "Shhhh"

  it("performs search requests") {
    val nResults = 10
    val responses = List(searchResponse(nResults))
    withApiClient(responses) {
      case (apiClient, _) =>
        whenReady(apiClient.search(query)) { response =>
          response shouldBe CalmSession(nResults, Cookie(cookie))
        }
    }
  }

  it("performs summary requests") {
    val responses = List(summaryResponse(List("RecordID" -> "1")))
    withApiClient(responses) {
      case (apiClient, _) =>
        whenReady(apiClient.summary(1)) { response =>
          response shouldBe CalmRecord(
            "1",
            Map("RecordID" -> List("1")),
            retrievedAt)
        }
    }
  }

  it("uses basic auth credentials for requests") {
    val responses =
      List(searchResponse(1), summaryResponse(List("RecordID" -> "1")))
    withApiClient(responses) {
      case (apiClient, testHttpClient) =>
        whenReady(for {
          _ <- apiClient.search(query)
          _ <- apiClient.summary(1)
        } yield ()) { _ =>
          testHttpClient.requests.map(_.headers.collect {
            case auth: Authorization => auth
          }) should contain only List(
            Authorization(BasicHttpCredentials(username, password))
          )
        }
    }
  }

  it("sets the SOAPAction header for requests") {
    val responses =
      List(searchResponse(1), summaryResponse(List("RecordID" -> "1")))
    withApiClient(responses) {
      case (apiClient, testHttpClient) =>
        whenReady(for {
          _ <- apiClient.search(query)
          _ <- apiClient.summary(1)
        } yield ()) { _ =>
          testHttpClient.requests.map(_.headers.collect {
            case auth: RawHeader => auth
          }) shouldBe List(
            List(
              RawHeader("SOAPAction", "http://ds.co.uk/cs/webservices/Search")),
            List(
              RawHeader(
                "SOAPAction",
                "http://ds.co.uk/cs/webservices/SummaryHeader"))
          )
        }
    }
  }

  it("removes suppressed fields from summary responses") {
    val responses =
      List(summaryResponse(List("RecordID" -> "1", suppressedField)))
    withApiClient(responses) {
      case (apiClient, _) =>
        whenReady(apiClient.summary(1)) { response =>
          response.id shouldBe "1"
          response.data.keys should not contain suppressedField._1
        }
    }
  }

  it("fails if there is no cookie in a search response") {
    val responses = List(searchResponse(1, None))
    withApiClient(responses) {
      case (apiClient, _) =>
        whenReady(apiClient.search(query).failed) { error =>
          error.getMessage shouldBe "Session cookie not found in CALM response"
        }
    }
  }

  it("fails on error responses from the API") {
    val responses = List(HttpResponse(500, Nil, "Oops", protocol))
    withApiClient(responses) {
      case (apiClient, _) =>
        whenReady(apiClient.search(query).failed) { error =>
          error.getMessage shouldBe "Unexpected status from CALM API: 500 Internal Server Error"
        }
    }
  }

  it("fails if there is no RecordID in a summary response") {
    val responses = List(summaryResponse(List("Beep" -> "Boop")))
    withApiClient(responses) {
      case (apiClient, _) =>
        whenReady(apiClient.summary(1).failed) { error =>
          error.getMessage shouldBe "RecordID not found"
        }
    }
  }

  implicit val summaryParser: CalmHttpResponseParser[CalmSummaryRequest] =
    CalmHttpResponseParser.createSummaryResponseParser(Set(suppressedField._1))

  def withApiClient[R](responses: List[HttpResponse])(
    testWith: TestWith[(CalmApiClient, CalmHttpTestClient), R]): R =
    withMaterializer { implicit mat =>
      implicit val ec = mat.executionContext
      implicit val httpClient = new CalmHttpTestClient(responses)
      testWith((new CalmApiClient(url, username, password), httpClient))
    }

}
