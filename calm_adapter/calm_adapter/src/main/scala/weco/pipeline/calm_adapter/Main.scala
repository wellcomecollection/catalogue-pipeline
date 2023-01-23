package weco.pipeline.calm_adapter

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import weco.typesafe.WellcomeTypesafeApp
import weco.typesafe.config.builders.EnrichConfig._
import weco.messaging.typesafe.{SNSBuilder, SQSBuilder}
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.config.SourceVHSBuilder
import weco.catalogue.source_model.Implicits._
import weco.pipeline.calm_api_client.AkkaHttpCalmApiClient

object Main extends WellcomeTypesafeApp {

  runWithConfig { config =>
    implicit val actorSystem: ActorSystem =
      ActorSystem("main-actor-system")
    implicit val ec: ExecutionContext =
      actorSystem.dispatcher

    val calmRetriever = new ApiCalmRetriever(
      apiClient = new AkkaHttpCalmApiClient(
        url = config.requireString("calm.api.url"),
        username = config.requireString("calm.api.username"),
        password = config.requireString("calm.api.password"),
      ),
      // See https://github.com/wellcomecollection/private/blob/main/2020-04-calm-suppressed-fields.md
      suppressedFields = config
        .requireString("calm.suppressedFields")
        .split(",")
        .toSet
    )

    new CalmAdapterWorkerService(
      SQSBuilder.buildSQSStream(config),
      SNSBuilder.buildSNSMessageSender(config, subject = "CALM adapter"),
      calmRetriever = calmRetriever,
      calmStore = new CalmStore(
        SourceVHSBuilder.build[CalmRecord](config)
      ),
    )
  }
}
