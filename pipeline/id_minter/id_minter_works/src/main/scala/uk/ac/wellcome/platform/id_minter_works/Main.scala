package uk.ac.wellcome.platform.id_minter_works

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.circe.Json
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
import uk.ac.wellcome.storage.streaming.Codec._

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val actorSystem: ActorSystem =
      AkkaBuilder.buildActorSystem()

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
      messageStream = BigMessagingBuilder.buildMessageStream[Json](config),
      rdsClientConfig = RDSBuilder.buildRDSClientConfig(config),
      identifiersTableConfig = identifiersTableConfig
    )
  }
}
