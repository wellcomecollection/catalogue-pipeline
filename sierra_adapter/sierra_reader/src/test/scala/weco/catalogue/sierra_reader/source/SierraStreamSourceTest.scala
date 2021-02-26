package weco.catalogue.sierra_reader.source

import akka.stream.scaladsl.Sink
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlMatching}
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.circe.Json
import io.circe.optics.JsonPath.root
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka

import java.net.SocketTimeoutException
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._

class SierraStreamSourceTest
    extends AnyFunSpec
    with SierraWireMock
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with Akka {

  it("reads from Sierra") {
    val sierraSource = SierraSource(sierraWireMockUrl, oauthKey, oauthSecret)(
      resourceType = "items",
      params = Map.empty)

    withMaterializer { implicit materializer =>
      val eventualJson = sierraSource.take(1).runWith(Sink.head[Json])

      whenReady(eventualJson) {
        root.id.string.getOption(_) shouldBe Some("1000001")
      }
    }
  }

  it("paginates through results") {
    val sierraSource = SierraSource(sierraWireMockUrl, oauthKey, oauthSecret)(
      resourceType = "items",
      params = Map("updatedDate" -> "[2013-12-10T17:16:35Z,2013-12-13T21:34:35Z]"))

    withMaterializer { implicit materializer =>
      val eventualJsonList = sierraSource.runWith(Sink.seq[Json])

      whenReady(eventualJsonList) {
        _ should have size 157
      }
    }
  }

  it("refreshes the access token if receives a unauthorized response") {
    stubFor(
      get(urlMatching("/bibs"))
        .inScenario("refresh token")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse().withStatus(401))
        .atPriority(1)
        .willSetStateTo("token expired"))

    stubFor(
      get(urlMatching("/token"))
        .inScenario("refresh token")
        .whenScenarioStateIs("token expired")
        .willSetStateTo("token refreshed"))

    stubFor(
      get(urlMatching("/bibs"))
        .inScenario("refresh token")
        .whenScenarioStateIs("token refreshed"))

    val sierraSource =
      SierraSource(sierraWireMockUrl, oauthKey, "wrong-secret")(
        resourceType = "bibs",
        params = Map.empty)

    withMaterializer { implicit materializer =>
      val eventualJson = sierraSource.take(1).runWith(Sink.head[Json])

      whenReady(eventualJson) { json =>
        root.id.string.getOption(json) shouldBe Some("1000001")
      }
    }
  }

  it("returns a sensible error message if it fails to authorize with the Sierra API") {
    stubFor(get(urlMatching("/bibs")).willReturn(aResponse().withStatus(401)))

    val sierraSource =
      SierraSource(sierraWireMockUrl, oauthKey, oauthSecret)(
        resourceType = "bibs",
        params = Map.empty).take(1)

    withMaterializer { implicit materializer =>
      val eventualJson = sierraSource.runWith(Sink.head[Json])

      whenReady(eventualJson.failed) { ex =>
        ex shouldBe a[RuntimeException]
        ex.getMessage should include("Unauthorized")
      }
    }
  }

  it("obeys the throttle rate for sierra api requests") {
    val sierraSource = SierraSource(
      apiUrl = sierraWireMockUrl,
      oauthKey = oauthKey,
      oauthSecret = oauthSecret,
      throttleRate = ThrottleRate(4, 1.second)
    )(
      resourceType = "items",
      params = Map("updatedDate" -> "[2013-12-10T17:16:35Z,2013-12-13T21:34:35Z]"))

    withMaterializer { implicit materializer =>
      val future = sierraSource.runWith(Sink.seq[Json])

      val startTime = Instant.now()
      val expectedDurationInMillis = 1000L

      whenReady(future) { _ =>
        val gap: Long = ChronoUnit.MILLIS.between(startTime, Instant.now())

        gap shouldBe >(expectedDurationInMillis)
      }
    }
  }

  it("respects the specified timeout parameter") {
    // The default timeout is 10000 ms, so with default settings we'd
    // expect to get a 200 OK for this response.
    stubFor(
      get(urlMatching("/bibs")).willReturn(
        aResponse()
          .withStatus(200)
          .withFixedDelay(5000)
      )
    )

    val source = SierraSource(
      apiUrl = sierraWireMockUrl,
      oauthKey = oauthKey,
      oauthSecret = oauthSecret,
      timeoutMs = 200
    )(
      resourceType = "bibs",
      params = Map.empty
    )

    withMaterializer { implicit materializer =>
      val future = source.take(1).runWith(Sink.head[Json])

      whenReady(future.failed) {
        _ shouldBe a[SocketTimeoutException]
      }
    }
  }
}
