package weco.catalogue.sierra_reader.source

import akka.stream.scaladsl.Sink
import io.circe.Json
import io.circe.optics.JsonPath.root
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.platform.sierra_reader.fixtures.WireMockFixture

import java.net.SocketTimeoutException
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._

class SierraStreamSourceTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with Akka
    with WireMockFixture {

  it("reads from Sierra") {
    val sierraSource = SierraSource("http://localhost:8080", oauthKey, oauthSecret)(
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
    val sierraSource = SierraSource("http://localhost:8080", oauthKey, oauthSecret)(
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
    // This test uses the three Wiremock fixture token_refresh*.json.
    val sierraSource =
      SierraSource(
        apiUrl = "http://localhost:8080",
        oauthKey = "refresh_token_key",
        oauthSecret = "refresh_token_secret"
      )(
        resourceType = "bibs",
        params = Map("token_refresh" -> "true"))

    withMaterializer { implicit materializer =>
      val eventualJson = sierraSource.take(1).runWith(Sink.head[Json])

      whenReady(eventualJson) { json =>
        root.id.string.getOption(json) shouldBe Some("1000001")
      }
    }
  }

  it("fails if it can't authenticate with the Sierra API") {
    // This test uses the Wiremock fixture bibs-unauthorized.json.
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
    // This test uses the Wiremock fixture bibs-timeout.json, which has
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
