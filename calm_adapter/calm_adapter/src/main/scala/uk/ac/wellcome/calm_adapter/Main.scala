package uk.ac.wellcome.calm_adapter

import scala.concurrent.ExecutionContext
import com.typesafe.config.Config
import akka.actor.ActorSystem
import akka.stream.Materializer
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.bigmessaging.VHSWrapper
import uk.ac.wellcome.bigmessaging.typesafe.VHSBuilder
import uk.ac.wellcome.json.JsonUtil._

object Main extends WellcomeTypesafeApp {

  runWithConfig { config =>
    implicit val ec: ExecutionContext =
      AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: Materializer =
      AkkaBuilder.buildMaterializer()
    implicit val httpClient: CalmHttpClient =
      new CalmAkkaHttpClient()

    new CalmAdapterWorkerService(
      SQSBuilder.buildSQSStream(config),
      SNSBuilder.buildSNSMessageSender(config, subject = "CALM adapter"),
      calmRetriever(config),
      calmStore(config),
    )
  }

  def calmRetriever(config: Config)(implicit
                                    ec: ExecutionContext,
                                    materializer: Materializer,
                                    httpClient: CalmHttpClient) =
    new HttpCalmRetriever(
      url = config.requireString("calm.api.url"),
      username = config.requireString("calm.api.username"),
      password = config.requireString("calm.api.password"),
      suppressedFields = config
        .requireString("calm.suppressedFields")
        .split(",")
        .toSet
    )

  def calmStore(config: Config) =
    new CalmStore(
      new VersionedStore(
        new VHSWrapper(
          VHSBuilder.build[CalmRecord](config)
        )
      )
    )
}
