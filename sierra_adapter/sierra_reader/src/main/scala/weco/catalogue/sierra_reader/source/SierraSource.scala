package weco.catalogue.sierra_reader.source

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.stream.ThrottleMode
import akka.stream.scaladsl.Source
import io.circe.Json
import uk.ac.wellcome.platform.sierra_reader.config.models.SierraAPIConfig
import weco.catalogue.source_model.sierra.identifiers.SierraRecordTypes
import weco.http.client.{AkkaHttpClient, HttpClient, HttpGet, HttpPost}
import weco.http.client.sierra.SierraOauthHttpClient

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class ThrottleRate(elements: Int, per: FiniteDuration, maximumBurst: Int)
case object ThrottleRate {
  def apply(elements: Int, per: FiniteDuration): ThrottleRate =
    ThrottleRate(elements, per, 0)
}

object SierraSource {
  def apply(
    config: SierraAPIConfig,
    throttleRate: ThrottleRate = ThrottleRate(elements = 0, per = 0 seconds)
  )(recordType: SierraRecordTypes.Value, params: Map[String, String])(
    implicit
    system: ActorSystem,
    ec: ExecutionContext): Source[Json, NotUsed] =
    applyWithClient(
      client = new SierraOauthHttpClient(
        underlying = new AkkaHttpClient() with HttpPost with HttpGet {
          override val baseUri: Uri = Uri(config.apiURL)
        },
        credentials = new BasicHttpCredentials(config.oauthKey, config.oauthSec)
      ),
      config = config,
      throttleRate = throttleRate
    )(
      recordType = recordType,
      params = params
    )

  def applyWithClient(
    client: HttpClient,
    config: SierraAPIConfig,
    throttleRate: ThrottleRate = ThrottleRate(elements = 0, per = 0 seconds)
  )(recordType: SierraRecordTypes.Value, params: Map[String, String])(
    implicit
    system: ActorSystem,
    ec: ExecutionContext): Source[Json, NotUsed] = {

    val pageSource = new SierraPageSource(
      config = config,
      client = client
    )

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
