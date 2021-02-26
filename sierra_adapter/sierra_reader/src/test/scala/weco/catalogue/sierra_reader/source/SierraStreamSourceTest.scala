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

  it("fails if it can't authenticate with the Sierra API") {
    val sierraSource = SierraSource(
      apiUrl = "http://localhost:8080",
      oauthKey = oauthKey,
      oauthSecret = oauthSecret
    )(
      resourceType = "bibs",
      params = Map("unauthorized" -> "true")
    )

    withMaterializer { implicit materializer =>
      val future = sierraSource.take(1).runWith(Sink.head[Json])

      whenReady(future.failed) { ex =>
        ex shouldBe a[RuntimeException]
        ex.getMessage shouldBe "Unable to refresh token!"
      }
    }
  }

  it("obeys the throttle rate for Sierra API requests") {
    val sierraSource = SierraSource(
      apiUrl = "http://localhost:8080",
      oauthKey = oauthKey,
      oauthSecret = oauthSecret,
      throttleRate = ThrottleRate(elements = 4, per = 1.second)
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
    // This test accompanies the Wiremock fixture bibs-timeout.json, which has
    // a fixed delay of 1000 milliseconds.
    val source = SierraSource(
      apiUrl = "http://localhost:8080",
      oauthKey = oauthKey,
      oauthSecret = oauthSecret,
      timeoutMs = 200
    )(
      resourceType = "bibs",
      params = Map("timeout" -> "true")
    )

    withMaterializer { implicit materializer =>
      val future = source.take(1).runWith(Sink.head[Json])

      whenReady(future.failed) {
        _ shouldBe a[SocketTimeoutException]
      }
    }
  }
}
