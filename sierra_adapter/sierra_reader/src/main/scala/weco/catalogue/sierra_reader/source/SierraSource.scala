package weco.catalogue.sierra_reader.source

import akka.NotUsed
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Source
import io.circe.Json

import scala.concurrent.duration._

case class ThrottleRate(elements: Int, per: FiniteDuration, maximumBurst: Int)
case object ThrottleRate {
  def apply(elements: Int, per: FiniteDuration): ThrottleRate =
    ThrottleRate(elements, per, 0)
}

object SierraSource {
  def apply(
    apiUrl: String,
    oauthKey: String,
    oauthSecret: String,
    throttleRate: ThrottleRate = ThrottleRate(elements = 0, per = 0 seconds),
    timeoutMs: Int = 10000
  )(resourceType: String,
    params: Map[String, String]): Source[Json, NotUsed] = {

    val source = Source.fromGraph(
      new SierraPageSource(
        apiUrl = apiUrl,
        oauthKey = oauthKey,
        oauthSecret = oauthSecret,
        timeoutMs = timeoutMs)(resourceType = resourceType, params = params)
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
