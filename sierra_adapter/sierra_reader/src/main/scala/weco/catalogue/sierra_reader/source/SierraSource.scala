package weco.catalogue.sierra_reader.source

import akka.NotUsed
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Source
import io.circe.Json
import uk.ac.wellcome.platform.sierra_reader.config.models.SierraAPIConfig

import scala.concurrent.duration._

case class ThrottleRate(elements: Int, per: FiniteDuration, maximumBurst: Int)
case object ThrottleRate {
  def apply(elements: Int, per: FiniteDuration): ThrottleRate =
    ThrottleRate(elements, per, 0)
}

object SierraSource {
  def apply(
    config: SierraAPIConfig,
    throttleRate: ThrottleRate = ThrottleRate(elements = 0, per = 0 seconds),
    timeout: Duration = 10 seconds
  )(resourceType: String,
    params: Map[String, String]): Source[Json, NotUsed] = {

    val source = Source.fromGraph(
      new SierraPageSource(config = config, timeout = timeout)(
        resourceType = resourceType,
        params = params)
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
