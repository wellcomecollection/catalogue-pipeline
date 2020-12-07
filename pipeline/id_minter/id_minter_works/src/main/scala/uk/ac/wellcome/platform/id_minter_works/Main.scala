package uk.ac.wellcome.platform.id_minter_works

import scala.concurrent.ExecutionContext
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
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.SQSBuilder
import uk.ac.wellcome.pipeline_storage.typesafe.{
  ElasticIndexerBuilder,
  ElasticRetrieverBuilder
}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.elasticsearch.IdentifiedWorkIndexConfig
import WorkState.Identified

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

    val workIndexer = ElasticIndexerBuilder[Work[Identified]](
      config,
      namespace = "identified-works",
      indexConfig = IdentifiedWorkIndexConfig
    )

    new IdMinterWorkerService(
      identifierGenerator = identifierGenerator,
      sender = BigMessagingBuilder.buildBigMessageSender(config),
      jsonRetriever = ElasticRetrieverBuilder[Json](
        config,
        namespace = "denormalised-works"),
      workIndexer = workIndexer,
      messageStream = SQSBuilder.buildSQSStream[NotificationMessage](config),
      rdsClientConfig = RDSBuilder.buildRDSClientConfig(config),
      identifiersTableConfig = identifiersTableConfig
    )
  }
}
