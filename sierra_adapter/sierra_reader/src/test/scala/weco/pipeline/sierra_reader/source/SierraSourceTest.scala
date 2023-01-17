package weco.pipeline.sierra_reader.source

import akka.http.scaladsl.model._
import akka.stream.scaladsl.Sink
import io.circe.Json
import io.circe.optics.JsonPath.root
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.sierra_reader.fixtures.WorkerServiceFixture
import weco.sierra.models.identifiers.SierraRecordTypes

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SierraSourceTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with WorkerServiceFixture {

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
               |""".stripMargin
          )
        )
      )
    )

    withActorSystem { implicit actorSystem =>
      val client = createClient(responses)
      val source = SierraSource(client)(
        recordType = SierraRecordTypes.items,
        params = Map())

      val future = source.take(1).runWith(Sink.head[Json])

      whenReady(future) {
        root.id.string.getOption(_) shouldBe Some("1461851")
      }
    }
  }

  it("fetches holdings from Sierra") {
    val responses = Seq(
      (
        HttpRequest(uri = Uri(
          s"$sierraUri/holdings?updatedDate=%5B2003-03-03T03:00:00Z,2003-04-04T04:00:00Z%5D&fields=updatedDate")),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{
              |  "total": 5,
              |  "start": 0,
              |  "entries": [
              |    {"id": 1047360, "updatedDate": "2003-04-02T13:22:11Z"},
              |    {"id": 1063913, "updatedDate": "2003-03-03T10:25:54Z"},
              |    {"id": 1063914, "updatedDate": "2003-03-03T10:26:35Z"},
              |    {"id": 1063915, "updatedDate": "2003-03-03T10:27:06Z"},
              |    {"id": 1063924, "updatedDate": "2003-03-03T10:27:44Z"}
              |  ]
              |}
              |""".stripMargin
          )
        )
      )
    )

    withActorSystem { implicit actorSystem =>
      val client = createClient(responses)
      val source = SierraSource(client)(
        recordType = SierraRecordTypes.holdings,
        params = Map(
          "updatedDate" -> "[2003-03-03T03:00:00Z,2003-04-04T04:00:00Z]",
          "fields" -> "updatedDate"))

      val future = source.take(1).runWith(Sink.head[Json])

      whenReady(future) {
        root.id.int.getOption(_) shouldBe Some(1047360)
      }
    }
  }

  it("paginates through results") {
    val responses = Seq(
      (
        HttpRequest(uri = Uri(s"$sierraUri/items")),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{
              |  "total": 5,
              |  "start": 0,
              |  "entries": [
              |    {"id": "1000001"},
              |    {"id": "1000002"},
              |    {"id": "1000003"},
              |    {"id": "1000004"},
              |    {"id": "1000005"}
              |  ]
              |}
              |""".stripMargin
          )
        )
      ),
      (
        HttpRequest(uri = Uri(s"$sierraUri/items?id=%5B1000006,%5D")),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{
              |  "total": 3,
              |  "start": 0,
              |  "entries": [
              |    {"id": "1000006"},
              |    {"id": "1000007"},
              |    {"id": "1000008"}
              |  ]
              |}
              |""".stripMargin
          )
        )
      ),
      (
        HttpRequest(uri = Uri(s"$sierraUri/items?id=%5B1000009,%5D")),
        HttpResponse(
          status = StatusCodes.NotFound,
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{
              |  "code": 107,
              |  "specificCode": 0,
              |  "httpStatus": 404,
              |  "name": "Record not found"
              |}
              |""".stripMargin
          )
        )
      )
    )

    withActorSystem { implicit actorSystem =>
      val client = createClient(responses)
      val source = SierraSource(client)(
        recordType = SierraRecordTypes.items,
        params = Map())

      val future = source.runWith(Sink.seq[Json])

      val ids = whenReady(future) { json =>
        json.map { root.id.string.getOption(_).get }
      }

      ids shouldBe Seq(
        "1000001",
        "1000002",
        "1000003",
        "1000004",
        "1000005",
        "1000006",
        "1000007",
        "1000008")
    }
  }

  it("fails if it can't authenticate with the Sierra API") {
    val responses = Seq(
      (
        HttpRequest(uri = Uri(s"$sierraUri/bibs")),
        HttpResponse(
          status = StatusCodes.Unauthorized,
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{"ok": false}
              |""".stripMargin
          )
        )
      )
    )

    withActorSystem { implicit actorSystem =>
      val client = createClient(responses)
      val source = SierraSource(client)(
        recordType = SierraRecordTypes.bibs,
        params = Map())

      val future = source.take(1).runWith(Sink.head[Json])

      whenReady(future.failed) { ex =>
        ex shouldBe a[Throwable]
        ex.getMessage should startWith(
          "Unexpected HTTP response: HttpResponse(401 Unauthorized")
      }
    }
  }

  it("obeys the throttle rate for Sierra API requests") {
    val responses = Seq(
      (
        HttpRequest(uri = Uri(s"$sierraUri/items")),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{"total": 1, "start": 0, "entries": [{"id": "1000001"}]}
              |""".stripMargin
          )
        )
      ),
      (
        HttpRequest(uri = Uri(s"$sierraUri/items?id=%5B1000002,%5D")),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{"total": 1, "start": 0, "entries": [{"id": "1000002"}]}
              |""".stripMargin
          )
        )
      ),
      (
        HttpRequest(uri = Uri(s"$sierraUri/items?id=%5B1000003,%5D")),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{"total": 1, "start": 0, "entries": [{"id": "1000003"}]}
              |""".stripMargin
          )
        )
      ),
      (
        HttpRequest(uri = Uri(s"$sierraUri/items?id=%5B1000004,%5D")),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{"total": 1, "start": 0, "entries": [{"id": "1000004"}]}
              |""".stripMargin
          )
        )
      ),
      (
        HttpRequest(uri = Uri(s"$sierraUri/items?id=%5B1000005,%5D")),
        HttpResponse(
          status = StatusCodes.NotFound,
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            """
              |{
              |  "code": 107,
              |  "specificCode": 0,
              |  "httpStatus": 404,
              |  "name": "Record not found"
              |}
              |""".stripMargin
          )
        )
      )
    )

    withActorSystem { implicit actorSystem =>
      val client = createClient(responses)
      val source = SierraSource(
        client,
        throttleRate = ThrottleRate(elements = 4, per = 1.second))(
        recordType = SierraRecordTypes.items,
        params = Map())

      val startTime = Instant.now()

      val future = source.runWith(Sink.seq[Json])

      // 5 requests should take at least a second
      val expectedDuration = 1 second

      whenReady(future) { _ =>
        val gap: Long = ChronoUnit.MILLIS.between(startTime, Instant.now())

        gap shouldBe >(expectedDuration.toMillis)
      }
    }
  }
}
