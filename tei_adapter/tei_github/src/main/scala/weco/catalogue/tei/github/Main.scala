package weco.catalogue.tei.github

import akka.actor.ActorSystem
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config =>
    implicit val ec: ExecutionContext = AkkaBuilder.buildExecutionContext()
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

    GitHubWorkerService(
      messageStream = SQSBuilder.buildSQSStream(config),
      messageSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "TEI github service")
    )
  }
}
