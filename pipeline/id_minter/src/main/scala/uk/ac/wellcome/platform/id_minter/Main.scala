package uk.ac.wellcome.platform.id_minter

import akka.actor.ActorSystem
import com.typesafe.config.Config
import io.circe.Json
import uk.ac.wellcome.elasticsearch.IdentifiedWorkIndexConfig
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.typesafe.{SNSBuilder, SQSBuilder}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.WorkState.Identified
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.typesafe.{ElasticIndexerBuilder, ElasticSourceRetrieverBuilder, PipelineStorageStreamBuilder}
import uk.ac.wellcome.platform.id_minter.config.builders.{IdentifiersTableBuilder, RDSBuilder}
import uk.ac.wellcome.platform.id_minter.database.IdentifiersDao
import uk.ac.wellcome.platform.id_minter.models.IdentifiersTable
import uk.ac.wellcome.platform.id_minter.services.IdMinterWorkerService
import uk.ac.wellcome.platform.id_minter.steps.IdentifierGenerator
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

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

    val esClient = ElasticBuilder.buildElasticClient(config)

    val workIndexer = ElasticIndexerBuilder[Work[Identified]](
      config,
      esClient,
      namespace = "identified-works",
      indexConfig = IdentifiedWorkIndexConfig
    )

    val messageStream = SQSBuilder.buildSQSStream[NotificationMessage](config)
    val messageSender = SNSBuilder
      .buildSNSMessageSender(config, subject = "Sent from the id-minter")

    new IdMinterWorkerService(
      messageStream,
      workIndexer,
      PipelineStorageStreamBuilder.buildPipelineStorageConfig(config),
      messageSender,
      identifierGenerator = identifierGenerator,
      jsonRetriever = ElasticSourceRetrieverBuilder[Json](
        config,
        esClient,
        namespace = "source-works"),
      rdsClientConfig = RDSBuilder.buildRDSClientConfig(config),
      identifiersTableConfig = identifiersTableConfig
    )
  }
}
