package weco.pipeline.calm_adapter

import java.time.LocalDate
import org.apache.pekko.http.scaladsl.model.headers._
import org.apache.pekko.stream.scaladsl._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.{RandomGenerators, TestWith}
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.calm_api_client.{
  CalmQuery,
  CalmSession,
  CalmSummaryRequest
}
import weco.pipeline.calm_api_client.fixtures.CalmApiClientFixtures

class CalmRetrieverTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with CalmApiClientFixtures
    with RandomGenerators {

  val query = CalmQuery.ModifiedDate(LocalDate.of(2000, 1, 1))
  val cookie = Cookie(randomAlphanumeric(), randomAlphanumeric())

  it("retrieves a list of CALM records from the API") {
    val existingRecords = List("1", "2").map(calmRecordWithId)
    withMaterializer {
      implicit materializer =>
        withCalmRetriever(existingRecords) {
          case (calmRetriever, _) =>
            whenReady(calmRetriever(query).runWith(Sink.seq[CalmRecord])) {
              records =>
                records shouldBe existingRecords
            }
        }
    }
  }

  it("uses the cookie from the first response for subsequent API requests") {
    val existingRecords = List("1", "2").map(calmRecordWithId)
    withMaterializer {
      implicit materializer =>
        withCalmRetriever(existingRecords) {
          case (calmRetriever, apiClient) =>
            whenReady(calmRetriever(query).runWith(Sink.seq[CalmRecord])) {
              _ =>
                val requestCookies = apiClient.requests.map {
                  case (_, requestCookie) => requestCookie
                }
                every(requestCookies.tail) shouldBe Some(cookie)
            }
        }
    }
  }

  it("uses num hits from the first response for subsequent API requests") {
    val existingRecords = List("1", "2", "3").map(calmRecordWithId)
    withMaterializer {
      implicit materializer =>
        withCalmRetriever(existingRecords) {
          case (calmRetriever, apiClient) =>
            whenReady(calmRetriever(query).runWith(Sink.seq[CalmRecord])) {
              _ =>
                val hitPositions = apiClient.requests.collect {
                  case (req: CalmSummaryRequest, _) => req.pos
                }
                hitPositions should contain theSameElementsAs List(0, 1, 2)
            }
        }
    }
  }

  def calmRecordWithId(id: String): CalmRecord = CalmRecord(
    id = id,
    data = List
      .fill(randomInt(1, 5)) {
        randomAlphanumeric(length = 4) -> List(randomAlphanumeric(length = 10))
      }
      .toMap,
    retrievedAt = randomInstant
  )

  def withCalmRetriever[R](
    records: List[CalmRecord]
  )(testWith: TestWith[(CalmRetriever, TestCalmApiClient), R]): R = {
    withTestCalmApiClient(
      handleSearch = _ => CalmSession(records.length, cookie),
      handleSummary = recordIndex => records(recordIndex)
    ) {
      apiClient =>
        testWith((new ApiCalmRetriever(apiClient), apiClient))
    }
  }
}
