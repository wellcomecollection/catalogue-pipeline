package uk.ac.wellcome.calm_adapter

import scala.concurrent.ExecutionContext
import com.typesafe.config.Config
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

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
    implicit val materializer: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()
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
                                    materializer: ActorMaterializer,
                                    httpClient: CalmHttpClient) =
    new HttpCalmRetriever(
      url = config.required[String]("calm.api.url"),
      username = config.required[String]("calm.api.username"),
      password = config.required[String]("calm.api.password"),
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
