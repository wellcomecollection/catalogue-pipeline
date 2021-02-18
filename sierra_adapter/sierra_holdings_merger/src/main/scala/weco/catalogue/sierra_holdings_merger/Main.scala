package weco.catalogue.sierra_holdings_merger

import akka.actor.ActorSystem
import com.typesafe.config.Config
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import weco.catalogue.sierra_holdings_merger.services.SierraHoldingsMergerWorkerService

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem = AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    new SierraHoldingsMergerWorkerService(
      sqsStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      messageSender = SNSBuilder
        .buildSNSMessageSender(config, subject = "Sierra Items to Dynamo")
    )
  }
}
