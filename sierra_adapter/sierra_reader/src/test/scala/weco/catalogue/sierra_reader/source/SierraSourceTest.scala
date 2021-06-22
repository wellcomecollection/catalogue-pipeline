package weco.catalogue.sierra_reader.source

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{Sink, Source}
import io.circe.Json
import io.circe.optics.JsonPath.root
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.sierra_reader.fixtures.WireMockFixture
import weco.catalogue.source_model.sierra.identifiers.SierraRecordTypes
import weco.http.client.{HttpGet, MemoryHttpClient}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class SierraSourceTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with Akka
    with WireMockFixture {

  val sierraUri = "http://sierra:1234"

  def withSource[R](responses: Seq[(HttpRequest, HttpResponse)])(recordType: SierraRecordTypes.Value, params: Map[String, String] = Map())(testWith: TestWith[Source[Json, NotUsed], R])(implicit system: ActorSystem): R = {
    val client = new MemoryHttpClient(responses) with HttpGet {
      override val baseUri: Uri = Uri(sierraUri)
    }

    val source = SierraSource.applyWithClient(client)(recordType, params)

    testWith(source)
  }

  it("reads from Sierra") {
    val responses = Seq(
      (
        HttpRequest(uri = Uri(s"$sierraUri/items")),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            s"""
               |{
               |  "total" : 5,
               |  "entries" : [
               |    {"id" : "1461851"},
               |    {"id" : "1461862"},
               |    {"id" : "1461864"},
               |    {"id" : "1462796"},
               |    {"id" : "1462800"}
               |  ]
               |}
               |
               |""".stripMargin
          )
        )
      )
    )

    withActorSystem { implicit actorSystem =>
      withSource(responses)(recordType = SierraRecordTypes.items) { source =>
        val future = source.take(1).runWith(Sink.head[Json])

        whenReady(future) {
          root.id.string.getOption(_) shouldBe Some("1461851")
        }
      }
    }
  }

  it("fetches holdings from Sierra") {
    withActorSystem { implicit actorSystem =>
      val sierraSource = SierraSource(sierraAPIConfig)(
        recordType = SierraRecordTypes.holdings,
        params = Map(
          "updatedDate" -> "[2003-03-03T03:00:00Z,2003-04-04T04:00:00Z]",
          "fields" -> "updatedDate"))

      val eventualJson = sierraSource.take(1).runWith(Sink.head[Json])

      whenReady(eventualJson) {
        root.id.int.getOption(_) shouldBe Some(1047360)
      }
    }
  }

  it("paginates through results") {
    withActorSystem { implicit actorSystem =>
      val sierraSource = SierraSource(sierraAPIConfig)(
        recordType = SierraRecordTypes.items,
        params =
          Map("updatedDate" -> "[2013-12-10T17:16:35Z,2013-12-13T21:34:35Z]"))

      val eventualJsonList = sierraSource.runWith(Sink.seq[Json])

      whenReady(eventualJsonList) {
        _ should have size 157
      }
    }
  }

  it("refreshes the access token if receives a unauthorized response") {
    // This test uses the three Wiremock fixture token_refresh*.json.
    val config = sierraAPIConfig.copy(
      oauthKey = "refresh_token_key",
      oauthSec = "refresh_token_secret"
    )

    withActorSystem { implicit actorSystem =>
      val sierraSource = SierraSource(config)(
        recordType = SierraRecordTypes.bibs,
        params = Map("token_refresh" -> "true"))

      val eventualJson = sierraSource.take(1).runWith(Sink.head[Json])

      whenReady(eventualJson) { json =>
        root.id.string.getOption(json) shouldBe Some("1000001")
      }
    }
  }

  it("fails if it can't authenticate with the Sierra API") {
    withActorSystem { implicit actorSystem =>
      // This test uses the Wiremock fixture bibs-unauthorized.json.
      val sierraSource = SierraSource(sierraAPIConfig)(
        recordType = SierraRecordTypes.bibs,
        params = Map("unauthorized" -> "true"))

      val future = sierraSource.take(1).runWith(Sink.head[Json])

      whenReady(future.failed) { ex =>
        ex shouldBe a[Throwable]
        ex.getMessage should startWith(
          "Unexpected HTTP response: HttpResponse(401 Unauthorized")
      }
    }
  }

  it("obeys the throttle rate for Sierra API requests") {
    withActorSystem { implicit actorSystem =>
      val sierraSource = SierraSource(
        config = sierraAPIConfig,
        throttleRate = ThrottleRate(elements = 4, per = 1.second)
      )(
        recordType = SierraRecordTypes.items,
        params =
          Map("updatedDate" -> "[2013-12-10T17:16:35Z,2013-12-13T21:34:35Z]"))

      val future = sierraSource.runWith(Sink.seq[Json])

      val startTime = Instant.now()
      val expectedDuration = 200 milliseconds

      whenReady(future) { _ =>
        val gap: Long = ChronoUnit.MILLIS.between(startTime, Instant.now())

        gap shouldBe >(expectedDuration.toMillis)
      }
    }
  }
}
