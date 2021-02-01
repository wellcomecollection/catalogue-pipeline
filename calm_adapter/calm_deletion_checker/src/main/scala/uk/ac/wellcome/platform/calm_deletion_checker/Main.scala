package uk.ac.wellcome.platform.calm_deletion_checker

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.platform.calm_api_client.{
  CalmAkkaHttpClient,
  CalmHttpClient,
  HttpCalmRetriever
}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val materializer: Materializer =
      AkkaBuilder.buildMaterializer()
    implicit val httpClient: CalmHttpClient =
      new CalmAkkaHttpClient()

    new DeletionCheckerWorkerService(
      SQSBuilder.buildSQSStream(config),
      SNSBuilder
        .buildSNSMessageSender(config, subject = "CALM deletion checker"),
      calmRetriever(config)
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
    )
}
