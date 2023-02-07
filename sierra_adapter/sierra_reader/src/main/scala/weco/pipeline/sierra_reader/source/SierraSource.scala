package weco.pipeline.sierra_reader.source

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Source
import io.circe.Json
import weco.http.client.HttpGet
import weco.sierra.models.identifiers.SierraRecordTypes

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class ThrottleRate(elements: Int, per: FiniteDuration, maximumBurst: Int)
case object ThrottleRate {
  def apply(elements: Int, per: FiniteDuration): ThrottleRate =
    ThrottleRate(elements, per, 0)
}

object SierraSource {
  def apply(
    client: HttpGet,
    throttleRate: ThrottleRate = ThrottleRate(elements = 0, per = 0 seconds)
  )(recordType: SierraRecordTypes.Value, params: Map[String, String])(
    implicit system: ActorSystem,
    ec: ExecutionContext
  ): Source[Json, NotUsed] = {

    val pageSource = new SierraPageSource(client = client)

    val source = Source.fromGraph(
      pageSource(
        recordType = recordType,
        params = params
      )
    )

    throttleRate.elements match {
      case 0 =>
        source
          .mapConcat(identity)
      case _ =>
        source
          .throttle(
            throttleRate.elements,
            throttleRate.per,
            throttleRate.maximumBurst,
            ThrottleMode.shaping
          )
          .mapConcat(identity)
    }
  }
}
