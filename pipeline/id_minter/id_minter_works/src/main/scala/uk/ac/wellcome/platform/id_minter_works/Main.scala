package uk.ac.wellcome.platform.id_minter_works

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem
import com.typesafe.config.Config

import uk.ac.wellcome.bigmessaging.typesafe.BigMessagingBuilder
import uk.ac.wellcome.platform.id_minter.config.builders.{
  IdentifiersTableBuilder,
  RDSBuilder
}
import uk.ac.wellcome.platform.id_minter.database.IdentifiersDao
import uk.ac.wellcome.platform.id_minter.models.IdentifiersTable
import uk.ac.wellcome.platform.id_minter_works.services.IdMinterWorkerService
import uk.ac.wellcome.platform.id_minter.steps.IdentifierGenerator
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()
    implicit val executionContext: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    val identifiersTableConfig = IdentifiersTableBuilder.buildConfig(config)
    RDSBuilder.buildDB(config)

    val identifierGenerator = new IdentifierGenerator(
      identifiersDao = new IdentifiersDao(
        identifiers = new IdentifiersTable(
          identifiersTableConfig = identifiersTableConfig
        )
      )
    )

    new IdMinterWorkerService(
      identifierGenerator = identifierGenerator,
      sender = BigMessagingBuilder.buildBigMessageSender(config),
      jsonRetriever = ???,
      messageStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      rdsClientConfig = RDSBuilder.buildRDSClientConfig(config),
      identifiersTableConfig = identifiersTableConfig
    )
  }
}
